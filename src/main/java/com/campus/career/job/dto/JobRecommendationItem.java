package com.campus.career.job.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 推荐列表中的一张岗位卡片，对应 architecture 4.3.1 的 {@code data} 元素。
 *
 * <p>在契约示例的 {@code jobId/title/reason/sourceUrl/isDemo} 之外，补两个 AC-3 字段：
 * <ul>
 *   <li>{@code linkStatus}：{@code OK} / {@code INVALID}，前端据此在卡片上标「失效来源」；</li>
 *   <li>{@code retryKeyword}：外链失效时供「按关键词重新检索」，可达时为 null 且不输出。</li>
 * </ul>
 *
 * @param reason 推荐理由，格式为「命中技能：Java、SQL」
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"jobId", "title", "reason", "sourceUrl", "isDemo", "linkStatus", "retryKeyword"})
public record JobRecommendationItem(String jobId,
                                    String title,
                                    String reason,
                                    String sourceUrl,
                                    // record 的 isXxx 访问器默认会被序列化成 xxx，显式固定为契约字段名
                                    @JsonProperty("isDemo") boolean isDemo,
                                    String linkStatus,
                                    String retryKeyword) {
}
