package com.campus.career.mock.dto;

/**
 * 开始面试响应，对应 architecture 4.5.1（HTTP 201）。
 *
 * @param sessionId      会话 ID，后续作答与归档都要带
 * @param status         恒为 {@code IN_PROGRESS}
 * @param totalQuestions 本次抽题数（FR-3 区间 5–8）
 * @param currentQuestion 第一道题
 */
public record StartInterviewResponse(String sessionId, String status,
                                     int totalQuestions, QuestionView currentQuestion) {
}
