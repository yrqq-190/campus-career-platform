package com.campus.career.holland.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campus.career.holland.HollandDimension;
import com.campus.career.holland.HollandQuestion;
import com.campus.career.holland.HollandQuestionBank;
import com.campus.career.holland.HollandScorer;
import com.campus.career.holland.profile.dto.DirectionView;
import com.campus.career.holland.profile.dto.ProfileView;
import com.campus.career.holland.profile.dto.SkillGapMissing;
import com.campus.career.holland.profile.dto.SkillGapView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 画像合成规则的单元测试，对应 spec/module-02 FR-5 ～ FR-8 与 AC-2、AC-5、AC-8。
 *
 * <p>不启动 Spring，直接喂计分结果与假档案，把「方向怎么选、短板怎么算、技能为空怎么降级」
 * 三件事分别钉住。
 */
class ProfileSynthesizerTest {

    private final ProfileSynthesizer synthesizer = new ProfileSynthesizer();

    @Test
    @DisplayName("AC-2：6 项技能时输出 3–5 个方向，每个方向都有支撑理由")
    void directionsHaveReasons() {
        ProfileView view = synthesizer.synthesize(
                resultWithHigh(HollandDimension.I),
                Optional.of(new ResumeSnapshot(
                        List.of("Java", "SQL", "Git", "沟通表达", "Excel", "英语"),
                        List.of(), List.of(), List.of())),
                new StudentIntent(List.of("杭州", "苏州"), List.of("互联网")));

        assertThat(view.directions()).hasSizeBetween(3, 5);
        for (DirectionView direction : view.directions()) {
            assertThat(direction.name()).isNotBlank();
            // 理由必须提到维度分数，不能只写一句「匹配度高」
            assertThat(direction.reason()).contains("维度").contains("分");
        }
    }

    @Test
    @DisplayName("AC-2：兴趣最高的方向排第一，且理由里带上已具备的技能")
    void topDirectionMatchesInterest() {
        ProfileView view = synthesizer.synthesize(
                resultWithHigh(HollandDimension.I),
                Optional.of(new ResumeSnapshot(List.of("Java", "SQL"), List.of(), List.of(), List.of())),
                StudentIntent.empty());

        DirectionView first = view.directions().get(0);
        assertThat(first.name()).isIn("后端开发", "数据分析");
        assertThat(first.reason()).contains("I 维度 100 分").contains("Java").contains("SQL");
    }

    @Test
    @DisplayName("方向选取覆盖不同维度：不会两个方向挤在同一个兴趣维度上")
    void directionsSpreadAcrossDimensions() {
        ProfileView view = synthesizer.synthesize(
                resultWithHigh(HollandDimension.E),
                Optional.of(new ResumeSnapshot(List.of("需求分析"), List.of(), List.of(), List.of())),
                StudentIntent.empty());

        List<String> names = view.directions().stream().map(DirectionView::name).toList();
        // 产品经理与市场营销同属 E 维度，最多只能出现一个
        assertThat(names.stream()
                .filter(n -> n.equals("产品经理") || n.equals("市场营销"))
                .count()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("AC-2 第 4 条：能力短板不超过 5 项，每项说明缺什么")
    void skillGapsCappedAtFive() {
        ProfileView view = synthesizer.synthesize(
                resultWithHigh(HollandDimension.I),
                Optional.of(new ResumeSnapshot(
                        List.of("Java", "SQL", "Git", "沟通表达", "Excel", "英语"),
                        List.of(), List.of(), List.of())),
                StudentIntent.empty());

        assertThat(view.skillGaps()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<SkillGapView> gaps = (List<SkillGapView>) view.skillGaps();
        assertThat(gaps).hasSizeLessThanOrEqualTo(5);
        for (SkillGapView gap : gaps) {
            assertThat(gap.skill()).isNotBlank();
            assertThat(gap.reason()).contains("方向要求");
        }
    }

    @Test
    @DisplayName("AC-2 第 3 条：意向城市与期望行业原样保留，不做去重排序")
    void intentKeptAsIs() {
        ProfileView view = synthesizer.synthesize(
                resultWithHigh(HollandDimension.S),
                Optional.of(new ResumeSnapshot(List.of("沟通表达"), List.of(), List.of(), List.of())),
                new StudentIntent(List.of("杭州", "苏州"), List.of("互联网")));

        assertThat(view.cities()).containsExactly("杭州", "苏州");
        assertThat(view.industries()).containsExactly("互联网");
    }

    @Test
    @DisplayName("验收条件第 9 条：意向未填时对应字段是空数组，不阻断生成")
    void emptyIntentIsEmptyArray() {
        ProfileView view = synthesizer.synthesize(
                resultWithHigh(HollandDimension.R),
                Optional.of(new ResumeSnapshot(List.of("Linux"), List.of(), List.of(), List.of())),
                StudentIntent.empty());

        assertThat(view.cities()).isEmpty();
        assertThat(view.industries()).isEmpty();
        assertThat(view.directions()).isNotEmpty();
    }

    @Test
    @DisplayName("AC-5：从未确认简历时，短板返回 PROFILE_SKILL_MISSING 标记而不是空数组")
    void skillMissingWhenResumeAbsent() {
        ProfileView view = synthesizer.synthesize(
                resultWithHigh(HollandDimension.A),
                Optional.empty(),
                new StudentIntent(List.of("上海"), List.of("互联网")));

        assertThat(view.skillGaps()).isInstanceOf(SkillGapMissing.class);
        assertThat(((SkillGapMissing) view.skillGaps()).status())
                .isEqualTo(SkillGapMissing.PROFILE_SKILL_MISSING);
        // 兴趣、城市、行业照常返回，画像不中断（AC-5）
        assertThat(view.directions()).hasSizeBetween(3, 5);
        assertThat(view.cities()).containsExactly("上海");
        assertThat(view.industries()).containsExactly("互联网");
    }

    @Test
    @DisplayName("AC-5：简历已确认但技能为空，同样走 PROFILE_SKILL_MISSING")
    void skillMissingWhenSkillsEmpty() {
        ProfileView view = synthesizer.synthesize(
                resultWithHigh(HollandDimension.C),
                Optional.of(new ResumeSnapshot(List.of(), List.of(), List.of(), List.of())),
                StudentIntent.empty());

        assertThat(view.skillGaps()).isInstanceOf(SkillGapMissing.class);
    }

    @Test
    @DisplayName("AC-5：技能缺失时推荐理由要说明技能未参与匹配，不能假装匹配过")
    void reasonMentionsMissingResume() {
        ProfileView view = synthesizer.synthesize(
                resultWithHigh(HollandDimension.I),
                Optional.empty(),
                StudentIntent.empty());

        assertThat(view.directions().get(0).reason()).contains("简历尚未确认");
    }

    @Test
    @DisplayName("AC-8：某维度 trace 不完整时拒绝生成画像")
    void untraceableScoresRejected() {
        Map<HollandDimension, Integer> scores = new EnumMap<>(HollandDimension.class);
        Map<HollandDimension, List<HollandScorer.ScoreItem>> trace = new EnumMap<>(HollandDimension.class);
        for (HollandDimension d : HollandDimension.values()) {
            scores.put(d, 50);
            // 只给 4 条，比题库规定的 5 题少一条，模拟分数没有完整来源
            List<HollandScorer.ScoreItem> items = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                items.add(new HollandScorer.ScoreItem(i, 2));
            }
            trace.put(d, items);
        }
        HollandScorer.Result broken = new HollandScorer.Result(scores, trace);

        assertThatThrownBy(() -> synthesizer.synthesize(broken, Optional.empty(), StudentIntent.empty()))
                .isInstanceOf(ScoreNotTraceableException.class);
    }

    @Test
    @DisplayName("技能比对忽略大小写：填 java 也能命中 Java 要求")
    void skillMatchIgnoresCase() {
        ProfileView view = synthesizer.synthesize(
                resultWithHigh(HollandDimension.I),
                Optional.of(new ResumeSnapshot(List.of("java", "sql"), List.of(), List.of(), List.of())),
                StudentIntent.empty());

        assertThat(view.directions().get(0).reason()).contains("已具备");
    }

    /**
     * 构造一份「某维度全部选非常符合、其余全部选非常不符合」的作答，让该维度得 100 分、
     * 其他维度 0 分，便于断言方向排序。
     */
    private HollandScorer.Result resultWithHigh(HollandDimension dimension) {
        Map<Integer, Integer> answers = new HashMap<>();
        for (int i = 1; i <= HollandQuestionBank.QUESTION_COUNT; i++) {
            answers.put(i, 1);
        }
        for (HollandQuestion question : HollandQuestionBank.all()) {
            if (question.dimension() == dimension) {
                answers.put(question.id(), 4);
            }
        }
        return HollandScorer.score(answers);
    }
}
