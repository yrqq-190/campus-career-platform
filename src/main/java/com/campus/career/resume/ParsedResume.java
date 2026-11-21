package com.campus.career.resume;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 Schema 校验后的四类字段，对应 spec FR-5 与 architecture 4.1.1 的 {@code data.parse}。
 *
 * <p>四类字段恒存在，简历中没有的类别是空数组而不是 null，也不省略（AC-4）。
 * 元素内部保留模型输出的键值，不在这里削成固定 POJO：spec FR-5 只说「每类按统一字段结构返回」，
 * 没有把每类的字段清单定死，提前固化等于自造契约。校验层只强制
 * architecture 4.1.1 示例里出现过的必填键，见 {@link ResumeParseSchema}。
 *
 * @param education   教育背景，元素为对象
 * @param skills      专业技能，元素为字符串
 * @param projects    项目经历，元素为对象
 * @param internships 实习经历，元素为对象
 */
public record ParsedResume(List<Map<String, Object>> education,
                           List<String> skills,
                           List<Map<String, Object>> projects,
                           List<Map<String, Object>> internships) {

    public ParsedResume {
        education = immutableObjects(education);
        skills = skills == null ? List.of() : List.copyOf(skills);
        projects = immutableObjects(projects);
        internships = immutableObjects(internships);
    }

    /** 四类字段全部为空。这是 AC-8 / {@code NO_VALID_FIELD} 的触发条件。 */
    public boolean isEmpty() {
        return education.isEmpty() && skills.isEmpty() && projects.isEmpty() && internships.isEmpty();
    }

    /**
     * 冻结每个条目并保持键顺序。
     *
     * <p>不用 {@code Map.copyOf}：它不保证顺序，序列化出来的字段顺序每次可能不同，
     * 前端 diff 与测试断言都会飘。
     */
    private static List<Map<String, Object>> immutableObjects(List<Map<String, Object>> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .map(entry -> Collections.unmodifiableMap(new LinkedHashMap<>(entry)))
                .toList();
    }
}
