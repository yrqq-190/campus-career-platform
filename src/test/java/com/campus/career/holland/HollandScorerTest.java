package com.campus.career.holland;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 计分与追溯的单元测试，对应 spec/module-02 FR-4 与 AC-1。
 *
 * <p>重点验两件事：归一化是否真的覆盖 0–100 全区间，以及每个维度的分数能否说清来源。
 */
class HollandScorerTest {

    @Test
    @DisplayName("全选非常不符合得 0 分：min-max 归一化下界可达")
    void lowestScore() {
        HollandScorer.Result result = HollandScorer.score(allAnswers(1));

        for (HollandDimension d : HollandDimension.values()) {
            assertThat(result.scores().get(d)).as(d.name()).isZero();
        }
    }

    @Test
    @DisplayName("全选非常符合得 100 分：min-max 归一化上界可达")
    void highestScore() {
        HollandScorer.Result result = HollandScorer.score(allAnswers(4));

        for (HollandDimension d : HollandDimension.values()) {
            assertThat(result.scores().get(d)).as(d.name()).isEqualTo(100);
        }
    }

    @Test
    @DisplayName("全选比较符合得 67 分：5 题各 3 分，raw=15，(15-5)/15*100 四舍五入")
    void middleScore() {
        HollandScorer.Result result = HollandScorer.score(allAnswers(3));

        for (HollandDimension d : HollandDimension.values()) {
            assertThat(result.scores().get(d)).as(d.name()).isEqualTo(67);
        }
    }

    @Test
    @DisplayName("单维度拉高不影响其他维度：R 维度一题满分其余最低，得 20 分")
    void oneDimensionRaised() {
        Map<Integer, Integer> answers = allAnswers(1);
        answers.put(1, 4);  // 第 1 题属 R 维度

        HollandScorer.Result result = HollandScorer.score(answers);

        // R: raw = 4 + 1 + 1 + 1 + 1 = 8 → (8-5)/15*100 = 20
        assertThat(result.scores().get(HollandDimension.R)).isEqualTo(20);
        for (HollandDimension d : HollandDimension.values()) {
            if (d != HollandDimension.R) {
                assertThat(result.scores().get(d)).as(d.name()).isZero();
            }
        }
    }

    @Test
    @DisplayName("AC-1 第 2 条：每个维度的分数都能回溯到 5 道具体题目")
    void traceIsComplete() {
        HollandScorer.Result result = HollandScorer.score(allAnswers(2));

        assertThat(result.allTraceable()).isTrue();
        for (HollandDimension d : HollandDimension.values()) {
            List<HollandScorer.ScoreItem> items = result.trace().get(d);
            assertThat(items).as(d.name()).hasSize(HollandQuestionBank.QUESTIONS_PER_DIMENSION);
            // 每条 trace 的题号必须真的属于该维度，不能张冠李戴
            for (HollandScorer.ScoreItem item : items) {
                assertThat(HollandQuestionBank.byId(item.questionId()).orElseThrow().dimension())
                        .isEqualTo(d);
            }
        }
    }

    @Test
    @DisplayName("题库结构：30 题、每维恰好 5 题、题号连续")
    void questionBankShape() {
        assertThat(HollandQuestionBank.all()).hasSize(30);
        for (int i = 1; i <= HollandQuestionBank.QUESTION_COUNT; i++) {
            assertThat(HollandQuestionBank.byId(i)).as("题号 " + i).isPresent();
        }
        for (HollandDimension d : HollandDimension.values()) {
            long count = HollandQuestionBank.all().stream().filter(q -> q.dimension() == d).count();
            assertThat(count).as(d.name()).isEqualTo(5);
        }
        assertThat(HollandQuestionBank.OPTIONS).hasSize(4);
    }

    @Test
    @DisplayName("题号越界时计分器直接拒绝，不做静默忽略")
    void illegalQuestionIdRejected() {
        Map<Integer, Integer> answers = allAnswers(2);
        answers.put(31, 2);

        assertThatThrownBy(() -> HollandScorer.score(answers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("31");
    }

    private Map<Integer, Integer> allAnswers(int option) {
        Map<Integer, Integer> answers = new HashMap<>();
        for (int i = 1; i <= HollandQuestionBank.QUESTION_COUNT; i++) {
            answers.put(i, option);
        }
        return answers;
    }
}
