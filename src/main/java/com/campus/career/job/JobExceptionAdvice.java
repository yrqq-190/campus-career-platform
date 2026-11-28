package com.campus.career.job;

import com.campus.career.common.ErrorCode;
import com.campus.career.common.RequestIdFilter;
import com.campus.career.job.dto.JobLinkInvalidResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * M3 专属的失败响应处理，只负责 AC-3 的 404：响应体比其他错误多
 * {@code retryKeyword}/{@code retryUrl} 两个顶层字段，{@code common.ApiResponse} 装不下。
 *
 * <p>与 M2 的 {@code QuizExceptionAdvice} 同理，必须声明 HIGHEST_PRECEDENCE：
 * common 的 advice 里有 {@code @ExceptionHandler(Exception.class)}，也会「能处理」
 * 本异常，不钉死顺序就可能被兜成 500。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class JobExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(JobExceptionAdvice.class);

    @ExceptionHandler(JobLinkInvalidException.class)
    public ResponseEntity<JobLinkInvalidResponse> handleLinkInvalid(JobLinkInvalidException ex,
                                                                    HttpServletRequest request) {
        ErrorCode code = ErrorCode.JOB_LINK_INVALID;
        String keyword = ex.retryKeyword() == null ? "" : ex.retryKeyword().trim();
        String retryUrl = JobCatalog.RETRY_SEARCH_BASE + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        // 只记错误码与岗位外链状态，不记学生身份与画像内容
        log.warn("岗位外链失效 uri={} code={}", request.getRequestURI(), code.name());
        return ResponseEntity.status(code.httpStatus())
                .body(new JobLinkInvalidResponse(code.name(), code.defaultMessage(),
                        requestId(request), keyword, retryUrl));
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }
}
