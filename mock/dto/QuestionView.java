package com.campus.career.mock.dto;

import com.campus.career.mock.MockQuestion;

/**
 * 题目视图，对应 architecture 4.5.1 的 {@code currentQuestion} 与 4.5.2 的 {@code nextQuestion}。
 *
 * @param questionId 会话内序号，从 1 起
 * @param content    题面
 * @param dimension  四维之一（FR-5）
 */
public record QuestionView(int questionId, String content, String dimension) {

    public static QuestionView of(MockQuestion question) {
        return new QuestionView(question.questionId(), question.content(), question.dimension());
    }
}
