package com.campus.career.holland.dto;

import java.util.List;
import java.util.Map;

/**
 * 取题与续答的响应，对应 architecture 4.2.1。
 *
 * <p>{@code answers} 回填已保存选项，是 AC-4「中途退出后从断点续答」的载体：
 * 前端拿到它就能把第 1–17 题渲染成已选中状态，第 18 题起留空。
 *
 * @param questions     固定 30 题，顺序固定（FR-1）
 * @param answers       题号 → 已选项，未作答的题不出现在里面
 * @param answeredCount 已作答题数，省得前端自己数
 * @param submitted     是否已提交，已提交则问卷只读
 */
public record QuestionsResponse(List<QuestionView> questions,
                                Map<Integer, Integer> answers,
                                int answeredCount,
                                boolean submitted) {
}
