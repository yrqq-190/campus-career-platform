package com.campus.career.holland.dto;

import com.campus.career.holland.HollandDimension;
import com.campus.career.holland.profile.dto.ProfileView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 提交问卷的响应，对应 architecture 4.2.3。
 *
 * <p>{@code scores} 序列化后就是契约里的 {@code {"R":72,"I":85,...}}：Jackson 对枚举 key
 * 默认用 {@code name()}，正好等于六维代号，不用额外转换器。
 *
 * <p>{@code profile} 由 T5 接上画像合成后填充，形态见 {@link ProfileView}。
 * 保留 {@code @JsonInclude(NON_NULL)} 是为了在画像合成抛异常时（AC-8 的 500）
 * 不至于出现一个半成品结构。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmitResponse(Map<HollandDimension, Integer> scores, ProfileView profile) {
}
