package com.campus.career.holland.profile;

import com.campus.career.common.ApiResponse;
import com.campus.career.common.ErrorCode;
import com.campus.career.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 画像模块的失败响应处理，只接 AC-8 这一种计分数据异常。
 *
 * <p>单独成类的原因与 {@code QuizExceptionAdvice} 一致：common 包不能反向依赖业务包。
 *
 * <p>与 {@code QuizExceptionAdvice} 同为最高优先级，两者处理的异常类型不重叠，
 * 不会互相抢；真正要防的是 common 里那个 {@code @ExceptionHandler(Exception.class)}
 * 兜底分支把 500 的响应体换成不可读的「服务内部错误」。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ProfileExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(ProfileExceptionAdvice.class);

    @ExceptionHandler(ScoreNotTraceableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotTraceable(ScoreNotTraceableException ex,
                                                                HttpServletRequest request) {
        // 只记类型与请求路径，不记六维分数与作答内容（NFR-2）
        log.error("计分无法追溯 uri={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ErrorCode.SCORE_NOT_TRACEABLE.httpStatus())
                .body(ApiResponse.error(ErrorCode.SCORE_NOT_TRACEABLE,
                        ErrorCode.SCORE_NOT_TRACEABLE.defaultMessage(), requestId(request)));
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }
}
