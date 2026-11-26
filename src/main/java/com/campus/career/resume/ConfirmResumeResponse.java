package com.campus.career.resume;

import java.time.Instant;

/**
 * {@code POST /api/v1/resumes/confirm} 的成功响应体，结构照抄 docs/architecture.md 4.1.2 的示例。
 *
 * <p>三个字段对应确认页保存后需要立刻回显的三件事：状态变了没有、什么时候确认的、
 * 一共确认了多少项技能（学生最容易反复改的就是技能列表，回一个数量让前端不必重算）。
 *
 * @param status      固定 {@code CONFIRMED}，表示内容已写入正式档案（AC-2）
 * @param confirmedAt 本次确认时间，重复确认时刷新为最新一次
 * @param skillCount  技能条数，与提交内容里的 {@code skills} 长度一致
 */
public record ConfirmResumeResponse(String status, Instant confirmedAt, int skillCount) {

    public static ConfirmResumeResponse of(ConfirmedResume resume) {
        return new ConfirmResumeResponse(
                resume.status(), resume.confirmedAt(), resume.content().skills().size());
    }
}
