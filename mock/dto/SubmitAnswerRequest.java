package com.campus.career.mock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 提交单题作答请求，对应 architecture 4.5.2。
 *
 * <p>这两个字段走 API 层 Bean Validation：契约没为「题号非法」「空回答」定义业务码，
 * 按 4.5.2 的「错误码补充说明」归通用参数校验，返回 400 且不带业务码。
 *
 * <p>用包装类型 {@code Integer} 而不是 {@code int}：{@code int} 的默认值是 0，
 * 客户端漏传 {@code questionId} 时会被当成「第 0 题」而不是「没传」，
 * {@link NotNull} 就永远不触发。
 *
 * @param questionId 会话内题号，从 1 起
 * @param answerText 作答原文，不能为空白
 */
public record SubmitAnswerRequest(
        @NotNull(message = "题号不能为空") @Min(value = 1, message = "题号从 1 开始") Integer questionId,
        @NotBlank(message = "回答不能为空") String answerText) {
}
