package com.campus.career.resume;

import com.campus.career.common.ApiResponse;
import com.campus.career.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * M1 简历上传与解析的 API 层，对应 docs/architecture.md 4.1.1。
 *
 * <p>API 层只做传输层防护与编排，业务判定全部在 {@link ResumeFileValidator}（服务层）。
 * 这是 architecture 第 2 章「四条层间约束」第 2 条的要求。
 *
 * <p>本 PR 的范围：413 / 415 两类边界校验 + 成功响应的结构。
 * 大模型解析调用与暂存区持久化随 T2 后续提交接上，见 Issue #103「口径说明」第 1 条。
 */
@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String STATUS_PENDING_CONFIRM = "PENDING_CONFIRM";

    private final ResumeFileValidator validator;

    public ResumeController(ResumeFileValidator validator) {
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResumeResponse>> upload(
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {

        validator.validate(file);

        // 解析链路未接入前，四类字段以空数组返回，保证「结构恒存在、不省略字段」（AC-4）。
        // 这里不做任何假解析，也不返回编造内容。
        var parse = new UploadResumeResponse.ParseStage(
                List.of(), List.of(), List.of(), List.of());
        var body = new UploadResumeResponse(nextResumeId(), STATUS_PENDING_CONFIRM, parse);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(requestId(request), body));
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
