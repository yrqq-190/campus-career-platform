package com.campus.career.mock;

/**
 * 会话内的一道题，对应 architecture 4.5.1 响应里的 {@code currentQuestion}。
 *
 * <p>{@code questionId} 是<b>会话内序号</b>（从 1 起连续），不是题库里的全局编号。
 * 契约里请求与响应都用 1、3、4 这种小整数（4.5.2 示例），若用全局题号，
 * 前端算「第几题」还要反查题库；用会话内序号则等于作答进度，前端无需额外映射。
 *
 * <p>{@code dimension} 只能取 {@link MockAnswerScorer} 里的四个常量之一（FR-5）。
 */
public record MockQuestion(int questionId, String content, String dimension) {
}
