package com.campus.career.holland.dto;

import com.campus.career.holland.HollandDimension;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 提交问卷的响应，对应 architecture 4.2.3。
 *
 * <p>{@code scores} 序列化后就是契约里的 {@code {"R":72,"I":85,...}}：Jackson 对枚举 key
 * 默认用 {@code name()}，正好等于六维代号，不用额外转换器。
 *
 * <p><b>{@code profile} 在 T4 恒为 null</b>，靠 {@link JsonInclude} 从响应里省略。
 * 画像合成（推荐方向、城市、行业、能力短板）属 T5 的 AC-2 / AC-3 / AC-5，
 * 需要读 M1 已确认档案，而 T1 建表还没合并。T4 只交计分，不提前编造画像内容。
 * T5 把类型换成固定的 ProfileView 并填值后，此处即符合契约完整形态。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmitResponse(Map<HollandDimension, Integer> scores, Object profile) {
}
