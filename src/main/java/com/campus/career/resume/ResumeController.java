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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * M1 简历上传与解析的 API 层，对应 docs/architecture.md 4.1.1。
 *
 * <p>API 层只做传输层防护与编排，业务判定全部在服务层：
 * 上传校验在 {@link ResumeFileValidator}，解析与降级在 {@link ResumeParseService}。
 * 这是 architecture 第 2 章「四条层间约束」第 2 条的要求。
 *
 * <p>本 PR（T3）补上 FR-4 的失败分支：504 {@code PARSE_TIMEOUT}、502 {@code MODEL_OUTPUT_INVALID}
 * 与附带检查项 422 {@code NO_VALID_FIELD}。失败响应由 {@code GlobalExceptionHandler} 统一成形，
 * 本类只管把服务层的异常放出去，不在 Controller 里拼错误体。
 */
@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String STATUS_PENDING_CONFIRM = "PENDING_CONFIRM";

    private final ResumeFileValidator validator;
    private final ResumeParseService parseService;

    public ResumeController(ResumeFileValidator validator, ResumeParseService parseService) {
        this.validator = validator;
        this.parseService = parseService;
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
