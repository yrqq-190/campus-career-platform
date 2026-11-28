package com.campus.career.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 一次模拟面试会话（M5），对应 {@code mock_interview} + {@code mock_interview_answer} 两张表。
 *
 * <p>字段按 db/schema.sql 的列名对齐，等 #123 的落库实现接上后，
 * 这个类就是两张表的聚合视图，Service 一行都不用改。
 *
 * <p><b>状态的唯一权威来源是本对象的 {@code status}</b>：4.5.2 要求「会话状态为 IN_PROGRESS 才可作答」，
 * 归档或取消后一律 409。用 {@code volatile} 而不是加锁，是因为写入只发生在归档/取消的两处显式赋值，
 * 读多写少；作答写入本身用 {@code synchronized} 保证「判定状态 + 记录作答」不被并发穿插。
 */
public final class MockInterviewSession {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final String sessionId;
    private final String studentId;
    private final String jobId;
    private final String jdText;
    private final List<MockQuestion> questions;

    /** 会话内已作答记录，key 为 questionId。LinkedHashMap 保留作答顺序，报告里按顺序出。 */
    private final Map<Integer, MockAnswer> answers = new LinkedHashMap<>();

    private volatile String status = STATUS_IN_PROGRESS;

    public MockInterviewSession(String sessionId, String studentId, String jobId,
                                String jdText, List<MockQuestion> questions) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.jobId = jobId;
        this.jdText = jdText;
        this.questions = List.copyOf(questions);
    }

    public String sessionId() {
        return sessionId;
    }

    public String studentId() {
        return studentId;
    }

    public String jobId() {
        return jobId;
    }

    public String jdText() {
        return jdText;
    }

    public String status() {
        return status;
    }

    public int totalQuestions() {
        return questions.size();
    }

    /** 按会话内序号取题；越界返回空，由 Service 决定映射成哪个状态码。 */
    public Optional<MockQuestion> question(int questionId) {
        return questions.stream().filter(q -> q.questionId() == questionId).findFirst();
    }

    /**
     * 记录一次作答。同一题重复提交时以最后一次为准（与 M2 FR-2「单题保存幂等」同口径），
     * 不新增第二条记录——契约没有为「重复作答同一题」定义 409，重复提交应覆盖而不是报错。
     */
    public synchronized void recordAnswer(MockQuestion question, String answerText,
                                         MockAnswerScorer.MockScore score) {
        answers.put(question.questionId(),
                new MockAnswer(question.questionId(), answerText, score.score(),
                        score.scoreStatus(), score.feedback()));
    }

    public synchronized Optional<MockAnswer> findAnswer(int questionId) {
        return Optional.ofNullable(answers.get(questionId));
    }

    public synchronized List<MockAnswer> answers() {
        return new ArrayList<>(answers.values());
    }

    /**
     * 下一道未作答的题，按会话内顺序取第一道。
     *
     * <p>不用「当前题号 +1」：AC-6 要求断网刷新后恢复上下文，学生可能跳题作答，
     * 按 +1 取会把已答过的题再推一遍。取「第一道未作答」天然兼容跳题与重连。
     */
    public synchronized Optional<MockQuestion> nextUnanswered() {
        return questions.stream().filter(q -> !answers.containsKey(q.questionId())).findFirst();
    }

    /** 归档或取消。只允许从 IN_PROGRESS 迁移，重复调用保持首次结果（归档幂等，AC-3）。 */
    public synchronized boolean moveTo(String targetStatus) {
        if (!STATUS_IN_PROGRESS.equals(status)) {
            return false;
        }
        status = targetStatus;
        return true;
    }

    /** 单题作答记录，对应 {@code mock_interview_answer} 一行。 */
    public record MockAnswer(int questionId, String answerText, int score,
                             String scoreStatus, String feedback) {
    }
}
