package com.campus.career.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 把异常统一收敛成 architecture 4.1.1 定义的失败响应体。
 *
 * <p>只认三种来源：
 * <ol>
 *   <li>服务层主动抛的 {@link BusinessException}，错误码一定在白名单内；
 *   <li>multipart 传输层拦截（文件超限、缺少文件字段），转成对应的业务码；
 *   <li>未预期的运行时异常，兜底 500，不把堆栈暴露给前端。
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 框架兜底码，不属于 spec 的业务码白名单，仅用于 500 场景。 */
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex,
                                                            HttpServletRequest request) {
        ErrorCode code = ex.errorCode();
        log.warn("业务异常 code={} uri={}", code.name(), request.getRequestURI());
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code, ex.getMessage(), requestId(request)));
    }

    /**
     * 文件超过 spring.servlet.multipart.max-file-size 时，Spring 在进入 Controller 前就抛出，
     * 服务层的 10MB 判定走不到，因此在传输层补一道，转成 AC-3 要求的 413。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex,
                                                             HttpServletRequest request) {
        log.warn("上传超限 uri={} cause={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ErrorCode.RESUME_TOO_LARGE.httpStatus())
                .body(ApiResponse.error(ErrorCode.RESUME_TOO_LARGE, null, requestId(request)));
    }

    /**
     * 请求里没有 file 字段。spec 与接口契约都没有为这个情形定义错误码，
     * 按「没有可判定的文件」归入格式不支持分支，不新增码。
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex,
                                                               HttpServletRequest request) {
        log.warn("缺少文件字段 uri={} part={}", request.getRequestURI(), ex.getRequestPartName());
        return ResponseEntity.status(ErrorCode.RESUME_FORMAT_UNSUPPORTED.httpStatus())
                .body(ApiResponse.error(ErrorCode.RESUME_FORMAT_UNSUPPORTED,
                        "请上传一份 PDF 或 Word 格式的简历", requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex,
                                                              HttpServletRequest request) {
        // 只记类型与消息，不记请求体，避免把简历内容写进日志（NFR-2）
        log.error("未预期异常 uri={} type={}", request.getRequestURI(), ex.getClass().getName(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(INTERNAL_ERROR, "服务内部错误，请稍后重试",
                        requestId(request), null));
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }
}
