package com.campus.career.holland.profile.dto;

/**
 * 一个推荐方向，对应 architecture 4.2.3 的 {@code profile.directions} 元素。
 *
 * @param name   方向名称
 * @param reason 支撑理由，必须说明依据的是哪几个兴趣维度或哪几项技能（AC-2 第 2 条）
 */
public record DirectionView(String name, String reason) {
}
