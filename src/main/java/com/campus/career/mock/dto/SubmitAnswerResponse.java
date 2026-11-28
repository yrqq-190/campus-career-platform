package com.campus.career.mock.dto;

/**
 * 提交单题作答响应，对应 architecture 4.5.2（HTTP 200）。
 *
 * @param questionId      本次评分的题号
 * @param score           0–100 整数（FR-5）
 * @param scoreStatus     {@code COMPLETE} 或 {@code PARTIAL}（AC-5）
 * @param feedback        命中关键词或「回答过于简短」的提示
 * @param hasNextQuestion 是否还有未作答的题
 * @param nextQuestion    {@code hasNextQuestion=false} 时为 {@code null}，前端据此引导生成报告
 */
public record SubmitAnswerResponse(int questionId, int score, String scoreStatus, String feedback,
                                   boolean hasNextQuestion, QuestionView nextQuestion) {
}
