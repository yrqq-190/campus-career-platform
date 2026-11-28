package com.campus.career.job.dto;

/**
 * 外链失效的失败响应，对应 module-03 AC-3：
 *
 * <pre>
 * { "code":"JOB_LINK_INVALID", "message":"该链接可能已失效，已为您按关键词重新检索",
 *   "requestId":"...", "retryKeyword":"Python 数据分析 实习生", "retryUrl":"https://..." }
 * </pre>
 *
 * <p>{@code retryKeyword}/{@code retryUrl} 放在顶层而不是 {@code data} 里，
 * 与 M2 未答完的 {@code missing} 同理：失败响应不带 data。
 */
public record JobLinkInvalidResponse(String code,
                                     String message,
                                     String requestId,
                                     String retryKeyword,
                                     String retryUrl) {
}
