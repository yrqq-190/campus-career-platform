package com.campus.career.mock;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * M5 预置题库与抽题（FR-3：按岗位类型抽 5–8 道）。
 *
 * <p>不接大模型、不读数据库：题库是代码常量，与 spec Non-Goals「不接入大模型进行实时题生成」一致。
 *
 * <p>抽题数固定 {@link #QUESTION_COUNT} = 5，取 FR-3 区间（5–8）的下界。理由是周内演示要可复现：
 * 每次开面试题目数不同，前端进度条与报告里的四维覆盖情况都会变，联调时对不上。
 * 每个岗位类型都给满 5 道且覆盖四个维度，保证报告四维都有分（AC-3、AC-5）。
 *
 * <p>岗位类型判定用 JD 关键词，命中不了就落到通用题库——这正是 FR-1 的降级路径：
 * M4 无投递记录时学生手填 JD，JD 里连岗位词都没有也能开面试，不能因此拦住流程。
 */
@Component
public class MockQuestionBank {

    public static final int QUESTION_COUNT = 5;

    private static final List<BankQuestion> BACKEND = List.of(
            new BankQuestion("请描述一次你解决线上故障的经历：故障现象、你如何定位、最后怎么恢复的。", MockAnswerScorer.DIMENSION_EXPERIENCE),
            new BankQuestion("解释一下数据库索引为什么能加速查询，什么情况下索引会失效。", MockAnswerScorer.DIMENSION_PROFESSIONAL),
            new BankQuestion("如果接口响应变慢，你会按什么顺序排查？请说明每一步想验证什么。", MockAnswerScorer.DIMENSION_LOGIC),
            new BankQuestion("你如何保证自己写的服务在高并发下不出问题？举一个你做过的具体措施。", MockAnswerScorer.DIMENSION_PROFESSIONAL),
            new BankQuestion("这个岗位要求熟悉 Java 与 Spring Boot，你哪段经历能证明你满足这条要求？", MockAnswerScorer.DIMENSION_MATCH));

    private static final List<BankQuestion> FRONTEND = List.of(
            new BankQuestion("请描述一次你排查页面白屏或渲染异常的经历，是怎么定位到根因的。", MockAnswerScorer.DIMENSION_EXPERIENCE),
            new BankQuestion("Vue 3 的响应式原理是什么？和 Vue 2 相比解决了什么问题。", MockAnswerScorer.DIMENSION_PROFESSIONAL),
            new BankQuestion("一个列表页首屏加载很慢，你会按什么顺序分析和优化？", MockAnswerScorer.DIMENSION_LOGIC),
            new BankQuestion("你如何组织一个中型前端项目的目录与组件分层？说说这样分的原因。", MockAnswerScorer.DIMENSION_PROFESSIONAL),
            new BankQuestion("这个岗位要求 Vue 3 + TypeScript，你的项目里是怎么落地这两项的？", MockAnswerScorer.DIMENSION_MATCH));

    private static final List<BankQuestion> DATA = List.of(
            new BankQuestion("请描述一次你用数据发现问题的经历：数据从哪来、你怎么验证结论。", MockAnswerScorer.DIMENSION_EXPERIENCE),
            new BankQuestion("什么情况下你会用窗口函数而不是子查询？举个你写过的例子。", MockAnswerScorer.DIMENSION_PROFESSIONAL),
            new BankQuestion("业务方给了一个模糊的取数需求，你会按什么步骤把它拆成可执行的指标？", MockAnswerScorer.DIMENSION_LOGIC),
            new BankQuestion("一份报表的数字和业务方预期不一致，你会怎么排查口径差异？", MockAnswerScorer.DIMENSION_PROFESSIONAL),
            new BankQuestion("这个岗位要求 SQL 与 Python 数据分析，你哪个项目最能体现这两项能力？", MockAnswerScorer.DIMENSION_MATCH));

    private static final List<BankQuestion> GENERAL = List.of(
            new BankQuestion("请介绍一段你最有成就感的经历：你负责什么、遇到什么困难、结果如何。", MockAnswerScorer.DIMENSION_EXPERIENCE),
            new BankQuestion("说说你掌握最扎实的一项专业技能，以及你是怎么练出来的。", MockAnswerScorer.DIMENSION_PROFESSIONAL),
            new BankQuestion("面对一个你没做过的任务，你会按什么步骤推进？请结合具体例子说明。", MockAnswerScorer.DIMENSION_LOGIC),
            new BankQuestion("和同伴有分歧时你怎么处理？举一个最后达成一致的例子。", MockAnswerScorer.DIMENSION_EXPERIENCE),
            new BankQuestion("你为什么投这个岗位？你的哪些经历和岗位要求是对得上的？", MockAnswerScorer.DIMENSION_MATCH));

    /**
     * 按 JD 抽题，返回带会话内序号（1 起）的题目列表。
     *
     * @param jdText 生效的 JD 文本，允许为空（落到通用题库）
     */
    public List<MockQuestion> draw(String jdText) {
        List<BankQuestion> bucket = pickBucket(jdText);
        int count = Math.min(QUESTION_COUNT, bucket.size());
        List<MockQuestion> questions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BankQuestion source = bucket.get(i);
            questions.add(new MockQuestion(i + 1, source.content(), source.dimension()));
        }
        return questions;
    }

    private List<BankQuestion> pickBucket(String jdText) {
        String text = jdText == null ? "" : jdText.toLowerCase();
        if (containsAny(text, "java", "spring", "mysql", "后端", "服务端", "并发", "微服务")) {
            return BACKEND;
        }
        if (containsAny(text, "vue", "react", "前端", "typescript", "javascript", "css", "页面")) {
            return FRONTEND;
        }
        if (containsAny(text, "数据", "分析", "sql", "报表", "指标", "bi", "python")) {
            return DATA;
        }
        return GENERAL;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 题库条目：只有题面与维度，序号在抽题时按顺序生成。 */
    private record BankQuestion(String content, String dimension) {
    }
}
