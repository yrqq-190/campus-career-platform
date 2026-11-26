package com.campus.career.holland.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.career.holland.HollandDimension;
import com.campus.career.holland.QuizRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link QuizRepository} 的存储契约，两个实现（内存 / JDBC）都必须原样通过。
 *
 * <h2>为什么要单独抽一层契约测试</h2>
 * 现有 {@code QuizControllerTest} 已经覆盖 AC-1、AC-4、AC-6、AC-7 的<b>端点行为</b>，
 * 但它测的是 HTTP 层，内部换掉 {@code InMemoryQuizRepository} 之后，
 * 「换实现有没有改变业务行为」这件事没有任何测试能挡住。
 *
 * <p>契约测试只依赖 {@link QuizRepository} 接口，不含任何具体实现类名。
 * 本周新增 JDBC 实现时，补一个子类即可让同一套用例跑第二遍，
 * 业务规则的回归网自动覆盖两份实现。
 *
 * <h2>刻意不放进本类的两条</h2>
 * <ul>
 *   <li><b>跨实例可见</b>（换一个仓储对象后仍读得到已提交状态）：内存实现天生做不到，
 *       它写在自己的 Map 里。这条属于「持久化的额外要求」，放 JDBC 子类单独断言。</li>
 *   <li><b>并发重复提交只成功一次</b>：需要多线程编排，属于 S1 并发用例，
 *       在 {@code QuizControllerTest} 与后续并发测试里覆盖，不混进契约层。</li>
 * </ul>
 *
 * <p>每个用例通过 {@link #newRepository()} 拿一个干净实现，子类不必写 {@code @BeforeEach} 清场。
 */
abstract class QuizRepositoryContractTest {

    /** 造一个刚创建、没有任何数据的实现。子类只需回答这一句。 */
    protected abstract QuizRepository newRepository();

    @Test
    @DisplayName("未作答时 findAnswers 返回空 Map，而不是 null")
    void absentAnswersIsEmptyMapNotNull() {
        Map<Integer, Integer> answers = newRepository().findAnswers("contract-empty");
        assertTrue(answers.isEmpty(),
                "未作答要返回空 Map。若返回 null，调用方每次都得判空，等于把契约漏洞推给上层");
    }

    @Test
    @DisplayName("单题保存后能原样读回（FR-2 即时保存）")
    void singleAnswerRoundTrip() {
        QuizRepository repository = newRepository();
        assertTrue(repository.saveAnswerIfNotSubmitted("contract-single", 3, 2),
                "未提交时保存应成功");

        assertEquals(2, repository.findAnswers("contract-single").get(3),
                "题 3 的选项应为刚保存的 2");
    }

    @Test
    @DisplayName("同题改选是覆盖，不会变成两条作答")
    void sameQuestionOverwrites() {
        QuizRepository repository = newRepository();
        repository.saveAnswerIfNotSubmitted("contract-overwrite", 7, 1);
        repository.saveAnswerIfNotSubmitted("contract-overwrite", 7, 4);

        Map<Integer, Integer> answers = repository.findAnswers("contract-overwrite");
        assertEquals(4, answers.get(7), "读回的应该是后一次的选择");
        assertEquals(1, answers.size(), "同题改选不得追加第二行，否则计分会重复计入");
    }

    @Test
    @DisplayName("AC-7：提交后再保存返回 false，且已提交的答案不被覆盖")
    void saveAfterSubmitIsRejected() {
        QuizRepository repository = newRepository();
        repository.saveAnswerIfNotSubmitted("contract-locked", 1, 3);
        assertTrue(repository.markSubmittedIfAbsent("contract-locked"), "首次提交应成功");

        assertFalse(repository.saveAnswerIfNotSubmitted("contract-locked", 1, 1),
                "已提交后保存必须返回 false，这是 AC-7 的仓储侧保证");
        assertEquals(3, repository.findAnswers("contract-locked").get(1),
                "被拒绝的保存不得改动已提交的选项");
    }

    @Test
    @DisplayName("AC-7：markSubmittedIfAbsent 首次返回 true，第二次返回 false")
    void markSubmittedOnlyOnce() {
        QuizRepository repository = newRepository();
        assertTrue(repository.markSubmittedIfAbsent("contract-twice"), "第一次提交应返回 true");
        assertFalse(repository.markSubmittedIfAbsent("contract-twice"),
                "第二次提交应返回 false，服务层靠这个返回值决定是否回 409");
    }

    @Test
    @DisplayName("未提交时 isSubmitted 为 false，提交后为 true")
    void isSubmittedFollowsMark() {
        QuizRepository repository = newRepository();
        assertFalse(repository.isSubmitted("contract-flag"), "从未提交时应为 false");

        repository.markSubmittedIfAbsent("contract-flag");
        assertTrue(repository.isSubmitted("contract-flag"), "标记提交后应为 true");
    }

    @Test
    @DisplayName("两个学生的作答互不串（studentId 隔离）")
    void answersAreIsolatedByStudent() {
        QuizRepository repository = newRepository();
        repository.saveAnswerIfNotSubmitted("contract-a", 1, 1);
        repository.saveAnswerIfNotSubmitted("contract-b", 1, 4);
        repository.markSubmittedIfAbsent("contract-a");

        assertTrue(repository.isSubmitted("contract-a"), "A 已提交");
        assertFalse(repository.isSubmitted("contract-b"), "B 不得因为 A 提交而变成已提交");
        assertEquals(1, repository.findAnswers("contract-a").get(1), "A 的答案不受 B 影响");
        assertEquals(4, repository.findAnswers("contract-b").get(1), "B 的答案不受 A 影响");
    }

    @Test
    @DisplayName("未写入分数时 findScores 返回空 Map")
    void absentScoresIsEmptyMap() {
        Map<HollandDimension, Integer> scores = newRepository().findScores("contract-noscore");
        assertTrue(scores.isEmpty(), "没有分数时返回空 Map，调用方据此判断画像是否已生成");
    }

    @Test
    @DisplayName("saveScores 写入的六维分数能被 findScores 原样读回")
    void scoresRoundTrip() {
        QuizRepository repository = newRepository();
        Map<HollandDimension, Integer> expected = Map.of(
                HollandDimension.R, 17,
                HollandDimension.I, 20,
                HollandDimension.A, 12,
                HollandDimension.S, 15,
                HollandDimension.E, 9,
                HollandDimension.C, 11);
        repository.saveScores("contract-score", expected);

        assertEquals(expected, repository.findScores("contract-score"),
                "六维分数必须逐项一致，差一项 top_dimension 就会算错");
    }

    @Test
    @DisplayName("两个学生的分数互不串")
    void scoresAreIsolatedByStudent() {
        QuizRepository repository = newRepository();
        repository.saveScores("contract-sa", Map.of(HollandDimension.R, 20));
        repository.saveScores("contract-sb", Map.of(HollandDimension.I, 20));

        assertEquals(20, repository.findScores("contract-sa").get(HollandDimension.R));
        assertFalse(repository.findScores("contract-sa").containsKey(HollandDimension.I),
                "A 的分数里不该出现只写给 B 的维度");
    }
}
