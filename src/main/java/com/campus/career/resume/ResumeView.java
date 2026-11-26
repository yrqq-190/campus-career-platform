package com.campus.career.resume;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 已确认档案的对外视图，对应 docs/architecture.md 4.1.3 的 {@code data} 段。
 *
 * <p>四类字段直接挂在 {@code data} 下，不再像 4.1.1 那样套一层 {@code parse}：
 * 4.1.1 里那一层的作用是把「解析结果」与「简历标识 / 状态」区分开，而本视图返回的就是一份已确认档案，
 * 只有这一个主体，再套一层没有区分作用，只会让下游取字段多绕一层。
 * 字段名与 {@link ParsedResume} 保持一致，M2 换成读档案的适配器时不需要改字段映射。
 *
 * <p>{@code status} 恒为 {@code CONFIRMED}：能读到这份视图，说明内容已经过学生显式确认（FR-6）；
 * 未确认的内容在暂存区，任何模块都不得读取，所以不存在「未确认」的视图形态。
 *
 * @param status      恒为 {@link ConfirmedResume#CONFIRMED}
 * @param confirmedAt 最近一次确认时间
 * @param education   教育背景，元素为对象
 * @param skills      专业技能，元素为字符串
 * @param projects    项目经历，元素为对象
 * @param internships 实习经历，元素为对象
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResumeView(String status,
                         Instant confirmedAt,
                         List<Map<String, Object>> education,
                         List<String> skills,
                         List<Map<String, Object>> projects,
                         List<Map<String, Object>> internships) {

    public static ResumeView of(ConfirmedResume resume) {
        ParsedResume content = resume.content();
        return new ResumeView(
                resume.status(),
                resume.confirmedAt(),
                content.education(),
                content.skills(),
                content.projects(),
                content.internships());
    }
}
