package com.campus.career.resume;

import com.campus.career.common.ApiResponse;
import com.campus.career.common.BusinessException;
import com.campus.career.common.ErrorCode;
import com.campus.career.common.RequestIdFilter;
import com.campus.career.common.StudentIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * M1 简历上传与解析的 API 层，覆盖 docs/architecture.md 4.1.1、4.1.2、4.1.3 三个端点。
 *
 * <p>API 层只做传输层防护与编排，业务判定全部在服务层：
 * 上传校验在 {@link ResumeFileValidator}，解析与降级在 {@link ResumeParseService}，
 * 确认入库与读取档案在 {@link ResumeArchiveService}。
 * 这是 architecture 第 2 章「四条层间约束」第 2 条的要求。
 *
 * <p>失败响应由 {@code GlobalExceptionHandler} 统一成形，
 * 本类只管把服务层的异常放出去，不在 Controller 里拼错误体。
 *
 * <p>三个端点共用 {@code /api/v1/resumes} 前缀，顺序与 4.1 的端点总览一致：
 * 上传并解析（4.1.1）→ 确认入库（4.1.2）→ 读取当前档案（4.1.3）。
 */
@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String STATUS_PENDING_CONFIRM = "PENDING_CONFIRM";

    private final ResumeFileValidator validator;
    private final ResumeParseService parseService;
    private final ResumeArchiveService archiveService;

    public ResumeController(ResumeFileValidator validator,
                            ResumeParseService parseService,
                            ResumeArchiveService archiveService) {
        this.validator = validator;
        this.parseService = parseService;
        this.archiveService = archiveService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResumeResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = StudentIdentity.HEADER, required = false) String studentHeader,
            HttpServletRequest request) {

        // 413 / 415：不落盘、不写暂存区、不建档（FR-2）。失败即抛出，后面的解析不会开始。
        validator.validate(file);

        String studentId = StudentIdentity.resolve(studentHeader);
        String requestId = requestId(request);

        // 504 / 502 / 422 在服务层抛出；抛了就一个字段都不会进暂存区，正式档案更是碰不到。
        ParsedResume parsed = parseService.parse(studentId, bytesOf(file), requestId);

        var parse = new UploadResumeResponse.ParseStage(
                parsed.education(), parsed.skills(), parsed.projects(), parsed.internships());
        var body = new UploadResumeResponse(nextResumeId(), STATUS_PENDING_CONFIRM, parse);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(requestId, body));
    }

    /**
     * 学生改完确认页后入库，对应 docs/architecture.md 4.1.2（FR-6、AC-2、AC-9）。
     *
     * <p>返回 {@code 200} 而不是上传端点的 {@code 201}：本端点不创建新资源，
     * 它把暂存内容变成正式档案，是同一份档案的状态迁移，4.1.2 写的就是 200 OK。
     *
     * <p>请求体缺失（{@code required = false}）按「什么都没提交」处理，落到同一个
     * {@code 422 NO_VALID_FIELD}。若交给 Spring 必填校验，抛出的
     * {@code HttpMessageNotReadableException} 在全局处理器里落到兜底分支，会返回 500 ——
     * 而「没提交任何内容」本来就是业务上的一种全空状态，用现成的码表达更准确，也不新增码。
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<ConfirmResumeResponse>> confirm(
            @RequestBody(required = false) ConfirmResumeRequest body,
            @RequestHeader(value = StudentIdentity.HEADER, required = false) String studentHeader,
            HttpServletRequest request) {

        String studentId = StudentIdentity.resolve(studentHeader);
        String requestId = requestId(request);

        ConfirmResumeRequest payload = body == null
                ? new ConfirmResumeRequest(null, null, null, null, null)
                : body;

        ConfirmedResume confirmedResume = archiveService.confirm(studentId, payload);

        return ResponseEntity.ok(ApiResponse.ok(requestId, ConfirmResumeResponse.of(confirmedResume)));
    }

    /**
     * 读取当前已确认档案，对应 docs/architecture.md 4.1.3（AC-2、AC-9）。
     *
     * <p><b>空态处理</b>：未建档时 {@code data} 为 {@code null}，是 200 而不是 404 ——
     * 「还没上传过简历」是一个正常状态，不是错误，所以不套用任何错误码。
     *
     * <p><b>为什么这里不用 {@link ApiResponse}</b>：4.1.3 要求空态输出字面的 {@code "data": null}，
     * 而 {@link ApiResponse} 的类级 {@code @JsonInclude(NON_NULL)} 会把为 null 的 {@code data} 整个省掉。
     * 这与 4.2.4 的画像是同一情形，处理方式也照 {@code ProfileResponse} 的先例：
     * 另造一个不带 NON_NULL 的壳，详见 {@link CurrentResumeResponse} 的类注释。
     */
    @GetMapping("/current")
    public ResponseEntity<CurrentResumeResponse> current(
            @RequestHeader(value = StudentIdentity.HEADER, required = false) String studentHeader,
            HttpServletRequest request) {

        String studentId = StudentIdentity.resolve(studentHeader);
        String requestId = requestId(request);

        ResumeView view = archiveService.current(studentId)
                .map(ResumeView::of)
                .orElse(null);

        return ResponseEntity.ok(CurrentResumeResponse.ok(requestId, view));
    }

    /** 校验已通过，此时读不出内容只可能是传输层出了问题，按不可读处理。 */
    private byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.RESUME_UNREADABLE, "文件无法读取");
        }
    }

    /**
     * 本周的占位标识。真正的序号要等 T1 建表后由库生成，
     * 这里只保证同一进程内不重复、格式与契约示例一致。
     */
    private String nextResumeId() {
        int serial = ThreadLocalRandom.current().nextInt(1, 1000);
        return "r_" + LocalDate.now().format(DATE) + "_" + String.format("%03d", serial);
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }
}
