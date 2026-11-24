package com.campus.career.resume;

import java.util.List;
import java.util.Map;

/**
 * {@code POST /api/v1/resumes/confirm} 的请求体，字段照 docs/architecture.md 4.1.2 的示例。
 *
 * <p>四类字段的结构与 {@link ParsedResume} 完全一致，因为确认页的编辑对象就是解析结果本身：
 * 学生可以改条目、删条目、新增条目，但字段结构不变（FR-6）。
 *
 * <p>{@code resumeId} 只作透传：前端把 4.1.1 返回的那个标识带回来，便于确认页与上传结果对上号。
 * 这里不给它加存在性校验，原因有两条，都指向同一个结论——不校验是对的：
 * <ol>
 *   <li>暂存区按 {@code student_id} 覆盖，同一学生任意时刻只有一条暂存记录（契约 5.1「幂等与无副作用」），
 *       结构上不可能出现「拿错一份暂存」的情况，校验它等于校验一个恒真的条件；</li>
 *   <li>契约 4.1.2 的失败表只定义了一个码 {@code 422 NO_VALID_FIELD}，
 *       若为「resumeId 不存在」加 404 或 409，就是自造码，与 A 组「全项目无自造码」的口径冲突。</li>
 * </ol>
 *
 * <p>字段为 {@code null} 时不报错，交由 {@link ParsedResume} 的构造器归一成空数组：
 * 前端删光某一类条目后可能不传该字段，这属于正常编辑结果，不是非法请求。
 *
 * @param resumeId    上传时返回的简历标识，透传，不参与校验
 * @param education   教育背景，元素为对象
 * @param skills      专业技能，元素为字符串
 * @param projects    项目经历，元素为对象
 * @param internships 实习经历，元素为对象
 */
public record ConfirmResumeRequest(String resumeId,
                                   List<Map<String, Object>> education,
                                   List<String> skills,
                                   List<Map<String, Object>> projects,
                                   List<Map<String, Object>> internships) {

    /**
     * 转成正式档案的内容载体。
     *
     * <p>直接复用 {@link ParsedResume} 而不是另造一个 {@code ConfirmedContent}：
     * 确认前后数据结构一致，入库时就没有字段级搬运，也就不会出现「搬漏一个字段」的静默丢失。
     */
    public ParsedResume toParsedResume() {
        return new ParsedResume(education, skills, projects, internships);
    }
}
