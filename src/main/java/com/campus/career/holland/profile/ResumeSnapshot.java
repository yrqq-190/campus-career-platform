package com.campus.career.holland.profile;

import java.util.List;

/**
 * M1 已确认档案的只读快照，是画像合成的第二类输入（spec/module-02 FR-5）。
 *
 * <p>只带画像用得上的部分：技能用于方向匹配与能力短板，教育/项目/实习暂作为理由的补充说明，
 * 不参与打分。字段保持简单，避免把 M1 的四类结构整个搬过来。
 *
 * <p>「已确认」是硬前提：FR-5 明确「未确认的解析结果不得作为画像输入」，
 * 所以取快照的接口只会返回学生点过确认库的那一版。
 */
public record ResumeSnapshot(List<String> skills,
                             List<String> education,
                             List<String> projects,
                             List<String> internships) {

    public ResumeSnapshot {
        skills = skills == null ? List.of() : List.copyOf(skills);
        education = education == null ? List.of() : List.copyOf(education);
        projects = projects == null ? List.of() : List.copyOf(projects);
        internships = internships == null ? List.of() : List.copyOf(internships);
    }

    /** 技能为空是 AC-5 的触发条件，单独给个可读的判断，避免各处写 {@code isEmpty()} 时漏判 null。 */
    public boolean hasNoSkills() {
        return skills.isEmpty();
    }
}
