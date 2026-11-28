package com.campus.career.mock;

import com.campus.career.common.ApiResponse;
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
 * M5 专属的失败响应处理，只认 {@link MockInterviewException}。
 *
 * <p>为什么不并进 {@code common.GlobalExceptionHandler}：那个类在 common 包里，
 * 让它 import M5 的异常会形成 common ←→ mock 的包级循环依赖，与 M2 的
 * {@code QuizExceptionAdvice} 同一处理方式。
 *
 * <p><b>必须声明 HIGHEST_PRECEDENCE</b>：common 那个 advice 里有
 * {@code @ExceptionHandler(Exception.class)}，对 {@link MockInterviewException} 也算「能处理」。
 * Spring 按 advice 的 Order 逐个尝试、遇到第一个「能处理」的就停，两者同序时顺序不确定，
 * 一旦 common 排前面就会把 400/404/409 全兜成 500。这里显式钉死顺序。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class MockExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(MockExceptionAdvice.class);

    @ExceptionHandler(MockInterviewException.class)
    public ResponseEntity<ApiResponse<Void>> handleMock(MockInterviewException ex,
                                                        HttpServletRequest request) {
        // 只记状态码与路径，不记 JD 与作答原文（NFR-2 同类要求：内容不进日志）
        log.warn("模拟面试异常 code={} status={} uri={}",
                ex.code(), ex.httpStatus().value(), request.getRequestURI());
        return ResponseEntity.status(ex.httpStatus())
                .body(new ApiResponse<>(ex.code(), ex.getMessage(), requestId(request), null));
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }
}
