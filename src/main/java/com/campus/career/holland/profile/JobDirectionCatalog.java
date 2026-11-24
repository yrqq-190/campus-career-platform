package com.campus.career.holland.profile;

import com.campus.career.holland.HollandDimension;
import java.util.List;

/**
 * 岗位方向池，画像推荐方向的候选集。
 *
 * <p>这是常量而不是数据库表，理由与题库一致：spec 的 Non-Goals 明确不做岗位库与站内投递
 * （属 MVP2），MVP1 的推荐只是「依据兴趣维度给出方向名称」，不需要运营可改的岗位表。
 * 真正的岗位数据在 M3，走外链跳转，不在这里。
 *
 * <p>六个维度各覆盖 1–2 个方向：I 与 A 与 E 各两个（这三类方向在校园场景里最常见），
 * R 与 S 与 C 各一个。方向总数 9，合成时按维度分排序、每个维度最多取一个，
 * 保证推荐结果不会挤在同一个兴趣维度上（AC-2 要 3–5 个方向，这里取 4 个）。
 *
 * <p>要求的技能名称会直接出现在 {@code skillGaps} 里，所以用学生看得懂的说法，
 * 不写成内部代号。
 */
public final class JobDirectionCatalog {

    /** 推荐方向数量，落在 AC-2 要求的 3–5 区间内。 */
    public static final int RECOMMEND_COUNT = 4;

    private static final List<JobDirection> DIRECTIONS = List.of(
            new JobDirection("后端开发", HollandDimension.I,
                    List.of("Java", "SQL", "Spring Boot", "Redis")),
            new JobDirection("数据分析", HollandDimension.I,
                    List.of("Python", "SQL", "统计分析")),
            new JobDirection("前端开发", HollandDimension.A,
                    List.of("JavaScript", "HTML/CSS", "Vue", "React")),
            new JobDirection("UI 设计", HollandDimension.A,
                    List.of("Figma", "交互设计", "视觉规范")),
            new JobDirection("产品经理", HollandDimension.E,
                    List.of("需求分析", "竞品分析", "沟通协调")),
            new JobDirection("市场营销", HollandDimension.E,
                    List.of("文案写作", "数据分析", "活动策划")),
            new JobDirection("人力资源", HollandDimension.S,
                    List.of("沟通表达", "招聘流程", "员工关系")),
            new JobDirection("测试工程", HollandDimension.C,
                    List.of("测试用例设计", "缺陷跟踪", "接口测试")),
            new JobDirection("运维开发", HollandDimension.R,
                    List.of("Linux", "Shell 脚本", "网络基础")));

    private JobDirectionCatalog() {
    }

    public static List<JobDirection> all() {
        return DIRECTIONS;
    }
}
