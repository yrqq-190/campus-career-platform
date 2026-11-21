package com.campus.career.holland.profile.dto;

/**
 * 技能缺失的降级标记，对应 AC-5 与 architecture 4.2.3：
 *
 * <pre>
 * "skillGaps": { "status": "PROFILE_SKILL_MISSING" }
 * </pre>
 *
 * <p><b>这不是错误码</b>（Issue #106 口径说明第 3 条）。它是 200 响应里的一个字段值，
 * 表示「画像照常生成，只是没法算短板」。前端不得据此弹错误提示，也不得因此中断展示。
 *
 * <p>之所以要有这么个对象而不是返回空数组：空数组的意思是「没有短板」，
 * 而实际意思是「不知道有没有短板，因为没有技能可比对」。两者语义相反，不能混用。
 */
public record SkillGapMissing(String status) {

    public static final String PROFILE_SKILL_MISSING = "PROFILE_SKILL_MISSING";

    public static SkillGapMissing of() {
        return new SkillGapMissing(PROFILE_SKILL_MISSING);
    }
}
