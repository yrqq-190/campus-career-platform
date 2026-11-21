package com.campus.career.holland.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 单题保存请求，对应 architecture 4.2.2。
 *
 * <p>题号与选项越界按契约走 API 层 Bean Validation 返 400，<b>不新增业务错误码</b>
 * （4.2.2 失败表第一行明确「—（无业务码）」）。
 *
 * <p>这里的 30 与 4 是契约里写死的取值范围，不是从题库算出来的；
 * 题库本身有 {@code HollandQuestionBank.QUESTION_COUNT} 与 {@code OPTION_MAX}，
 * 两者是否一致由测试 {@code QuizControllerTest#题目范围与题库常量一致} 兜住。
 */
public record SaveAnswerRequest(
        @Min(value = 1, message = "题号从 1 开始") @Max(value = 30, message = "题号最大为 30") int questionId,
        @Min(value = 1, message = "选项从 1 开始") @Max(value = 4, message = "选项最大为 4") int option) {
}
