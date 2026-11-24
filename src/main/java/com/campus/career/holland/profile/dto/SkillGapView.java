package com.campus.career.holland.profile.dto;

/**
 * 一项能力短板，对应 architecture 4.2.3 的 {@code profile.skillGaps} 元素。
 *
 * @param skill  缺的技能名
 * @param reason 说明缺的是什么、为什么算短板（AC-2 第 4 条）
 */
public record SkillGapView(String skill, String reason) {
}
