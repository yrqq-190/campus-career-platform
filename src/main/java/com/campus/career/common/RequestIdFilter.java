package com.campus.career.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求生成 requestId，写进 MDC、请求属性与响应头。
 *
 * <p>依据 spec/module-01 的 NFR-3：每次解析请求记录 requestId、开始与结束时间、总耗时、
 * 是否发生重试、最终错误码。NFR-2 同时要求日志里不得出现简历正文与个人信息，
 * 因此这里只放标识，不放任何文件内容。
 *
 * <p>格式与 architecture 4.1.1 示例一致（8 位十六进制）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String ATTR = "requestId";
    public static final String HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        request.setAttribute(ATTR, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(ATTR, requestId);
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long costMs = (System.nanoTime() - startedAt) / 1_000_000;
            // 只记标识与耗时，不记文件内容
            log.info("{} {} -> {} | {}ms", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), costMs);
            MDC.remove(ATTR);
        }
    }
}
