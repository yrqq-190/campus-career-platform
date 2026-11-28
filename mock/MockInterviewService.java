package com.campus.career.mock;

import com.campus.career.mock.dto.QuestionView;
import com.campus.career.mock.dto.StartInterviewResponse;
import com.campus.career.mock.dto.SubmitAnswerResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * M5 的业务层，对应 architecture 4.5.1（开始面试）与 4.5.2（单题作答评分）。
 *
 * <p>职责边界：JD 必填与长度判定、会话归属与状态判定、抽题、评分、记录作答都在这里；
 * Controller 只做路由与参数格式校验。
 *
 * <p><b>本类不实现 4.5.3（归档报告）</b>：AC-3 的归档幂等口径还要与 M8 看板对齐，
 * 属下一个任务；那一步只需在本类加一个 {@code report(...)}，不需要改现有两个方法。
 */
@Service
public class MockInterviewService {

    /** JD 最短长度。spec 边界表：粘贴文本过短（如 &lt;50 字）要提示「请粘贴更详细的岗位要求」。 */
    static final int MIN_JD_LENGTH = 50;

    private static final DateTimeFormatter SESSION_ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final MockInterviewRepository repository;
    private final MockQuestionBank questionBank;
    private final MockAnswerScorer scorer;

    /** 会话 ID 后三位自增，保证同一秒内多次开始面试也不重号（契约示例形如 {@code mi_20261112_001}）。 */
    private final AtomicInteger sequence = new AtomicInteger();

    public MockInterviewService(MockInterviewRepository repository,
                                MockQuestionBank questionBank,
                                MockAnswerScorer scorer) {
        this.repository = repository;
        this.questionBank = questionBank;
        this.scorer = scorer;
    }

    /**
     * 开始面试（4.5.1、AC-1、AC-4）。
     *
     * <p>M4（投递记录）尚未实现，无法按 {@code jobId} 回查岗位 JD，因此<b>无论是否传 jobId，
     * 都以 {@code jdText} 作为取题依据</b>——这正是 FR-1 的降级路径（「若无记录，支持用户手动粘贴 JD」）。
     * {@code jobId} 仍原样存进会话，等 M4 接上后改为「优先读投递记录、读不到再降级」，
     * 对外契约不变。
     */
    public StartInterviewResponse start(String studentId, String jobId, String jdText) {
        String effectiveJd = jdText == null ? "" : jdText.trim();
        if (effectiveJd.length() < MIN_JD_LENGTH) {
            throw MockInterviewException.jdRequired();
        }

        List<MockQuestion> questions = questionBank.draw(effectiveJd);
        MockInterviewSession session = new MockInterviewSession(
                nextSessionId(), studentId, blankToNull(jobId), effectiveJd, questions);
        repository.create(session);

        return new StartInterviewResponse(session.sessionId(), session.status(),
                session.totalQuestions(), QuestionView.of(questions.get(0)));
    }

    /**
     * 提交单题作答并评分（4.5.2、AC-2、AC-5）。
     *
     * <p>判定顺序固定为「会话存在 → 归属 → 状态 → 题号」，任何一步不过都不写数据：
     * 归属与存在性合并成同一个 404，是为了不向调用方暴露「这个 sessionId 存在但不属于你」。
     */
    public SubmitAnswerResponse answer(String studentId, String sessionId,
                                       int questionId, String answerText) {
        MockInterviewSession session = repository.find(sessionId)
                .orElseThrow(MockInterviewException::sessionNotFound);
        if (!session.studentId().equals(studentId)) {
            throw MockInterviewException.sessionNotFound();
        }
        if (!MockInterviewSession.STATUS_IN_PROGRESS.equals(session.status())) {
            throw MockInterviewException.sessionNotAnswerable();
        }
        MockQuestion question = session.question(questionId)
                .orElseThrow(MockInterviewException::sessionNotFound);

        MockAnswerScorer.MockScore score = scorer.score(question.dimension(), answerText);
        session.recordAnswer(question, answerText, score);
        repository.save(session);

        Optional<MockQuestion> next = session.nextUnanswered();
        return new SubmitAnswerResponse(questionId, score.score(), score.scoreStatus(),
                score.feedback(), next.isPresent(),
                next.map(QuestionView::of).orElse(null));
    }

    private String nextSessionId() {
        return "mi_" + LocalDateTime.now().format(SESSION_ID_TIME)
                + "_" + String.format("%03d", sequence.incrementAndGet());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
