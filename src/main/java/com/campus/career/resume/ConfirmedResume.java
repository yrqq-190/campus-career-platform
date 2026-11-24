package com.campus.career.resume;

import java.time.Instant;

/**
 * 正式档案：学生点过「确认入库」之后生效的那一份，是画像的唯一输入来源（FR-6、FR-5）。
 *
 * <p>与暂存区共用 {@link ParsedResume} 作为内容载体：两者结构完全一致，
 * 区别只在「有没有被确认」。同一份数据结构不给两个类，
 * 避免确认入库时还要做一次字段级搬运、搬漏一个字段就成了静默的数据丢失。
 *
 * @param content     四类字段
 * @param status      档案状态。正式档案恒为 {@link #CONFIRMED}，写在这里是为了让
 *                    AC-9 的「确认状态不变」有可断言的对象
 * @param confirmedAt 最近一次确认时间
 */
public record ConfirmedResume(ParsedResume content, String status, Instant confirmedAt) {

    /** 已确认状态。与 architecture 4.1.2 成功响应里的 {@code status} 取值一致。 */
    public static final String CONFIRMED = "CONFIRMED";

    public ConfirmedResume {
        if (content == null) {
            throw new IllegalArgumentException("档案内容不能为空");
        }
        status = status == null || status.isBlank() ? CONFIRMED : status;
        confirmedAt = confirmedAt == null ? Instant.now() : confirmedAt;
    }

    /** 用一份解析结果构造已确认档案。 */
    public static ConfirmedResume of(ParsedResume content) {
        return new ConfirmedResume(content, CONFIRMED, Instant.now());
    }

    public boolean isConfirmed() {
        return CONFIRMED.equals(status);
    }
}
