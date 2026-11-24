package com.campus.career.holland;

import com.campus.career.common.ErrorCode;
import com.campus.career.common.RequestIdFilter;
import com.campus.career.holland.dto.QuizIncompleteResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * M2 专属的失败响应处理，只负责 422 这一种带附加字段的错误。
 *
 * <p>为什么不并进 {@code common.GlobalExceptionHandler}：那个类在 common 包里，
 * 让它 import {@link QuizIncompleteException} 会形成 common ←→ holland 的包级循环依赖。
 * 业务模块自己收自己的异常，common 只留与业务无关的通用分支。
 *
 * <p><b>必须声明 HIGHEST_PRECEDENCE</b>：Spring 解析异常时按 advice 的 Order 逐个尝试，
 * 拿到第一个「能处理」的就停，而不是全局挑最具体的处理方法来比。common 那个 advice 里有
 * {@code @ExceptionHandler(Exception.class)}，它对 {@link QuizIncompleteException} 同样算
 * 「能处理」，若两者 Order 相同（默认都是最低优先级），排序不确定，
 * 一旦 common 排前面就会把 422 兜成 500。这里显式提到最高优先级把顺序钉死。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class QuizExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(QuizExceptionAdvice.class);

    @ExceptionHandler(QuizIncompleteException.class)
    public ResponseEntity<QuizIncompleteResponse> handleQuizIncomplete(QuizIncompleteException ex,
                                                                       HttpServletRequest request) {
        // 只记未答题数量，不记题号明细与所选答案（NFR-2 画像与作答不写日志）
        log.warn("问卷未答完 uri={} missingCount={}", request.getRequestURI(), ex.missing().size());
        return ResponseEntity.status(ErrorCode.QUIZ_INCOMPLETE.httpStatus())
                .body(new QuizIncompleteResponse(ErrorCode.QUIZ_INCOMPLETE.name(), ex.getMessage(),
                        requestId(request), ex.missing()));
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }
}
