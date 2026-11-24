package com.campus.career.resume;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/v1/resumes/current} 的成功响应体，对应 docs/architecture.md 4.1.3。
 *
 * <p>已建档时返回四类字段与 {@code status = CONFIRMED}；未建档时整个 {@code data} 为 {@code null}，
 * 空态不是错误、不返回 404 —— 这条口径写在 4.1.3 正文里，是全项目对「空态」的统一处理方式。
 *
 * <p>四类字段直接挂在 {@code data} 下，不再像 4.1.1 那样套一层 {@code parse}：
 * 4.1.1 里那一层的作用是把「解析结果」与「简历标识 / 状态」区分开，而本端点返回的就是一份已确认档案，
 * 只有这一个主体，再套一层没有区分作用，只会让下游取字段多绕一层。
 * 字段名与 {@link ParsedResume} 保持一致，M2 换成读档案的适配器时不需要改字段映射。
 *
 * @param status      恒为 {@code CONFIRMED}，与 {@link ConfirmedResume#CONFIRMED} 同一个取值
 * @param confirmedAt 最近一次确认时间
 * @param education   教育背景，元素为对象
 * @param skills      专业技能，元素为字符串
 * @param projects    项目经历，元素为对象
 * @param internships 实习经历，元素为对象
 */
public record CurrentResumeResponse(String status,
                                    Instant confirmedAt,
                                    List<Map<String, Object>> education,
                                    List<String> skills,
                                    List<Map<String, Object>> projects,
                                    List<Map<String, Object>> internships) {

    public static CurrentResumeResponse of(ConfirmedResume resume) {
        ParsedResume content = resume.content();
        return new CurrentResumeResponse(
                resume.status(),
                resume.confirmedAt(),
                content.education(),
                content.skills(),
                content.projects(),
                content.internships());
    }
}
