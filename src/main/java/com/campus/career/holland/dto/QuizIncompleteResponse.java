package com.campus.career.holland.dto;

import java.util.List;

/**
 * 未答完提交的失败响应，对应 architecture 4.2.3 的 422 响应体：
 *
 * <pre>
 * { "code":"QUIZ_INCOMPLETE", "message":"还有 20 题未作答", "requestId":"c31e77a0", "missing":[11,12,...,30] }
 * </pre>
 *
 * <p>{@code missing} 在顶层而不是放在 data 里，是契约原样，别改位置。
 */
public record QuizIncompleteResponse(String code, String message, String requestId, List<Integer> missing) {
}
