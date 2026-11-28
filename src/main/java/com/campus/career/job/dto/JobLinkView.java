package com.campus.career.job.dto;

/**
 * 点击可达外链的响应 {@code data}。
 *
 * <p>只回传跳转所需的岗位标识与链接，不返回岗位正文（module-03 FR-3、Non-Goals）。
 */
public record JobLinkView(String jobId, String sourceUrl) {
}
