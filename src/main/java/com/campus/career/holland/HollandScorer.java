package com.campus.career.holland;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 计分与追溯，对应 spec/module-02 FR-4 与 AC-1。
 *
 * <p>属服务层的纯函数计算，不碰数据库、不调大模型（NFR-1 要求「计分与画像合成为纯规则计算」）。
 *
 * <h2>归一化口径</h2>
 * 每题四档选项取值 1–4，每个维度固定 5 题，所以原始分区间是 [5, 20]。归一化用 min–max 映射：
 *
 * <pre>
 *   normalized = round((raw - 5) / (20 - 5) * 100)
 * </pre>
 *
 * <p>选这个而不是简单的 {@code raw / 20 * 100}，是因为后者最低只能得到 25 分，
 * 0–100 区间的下半段永远取不到，雷达图上「完全不感兴趣」和「兴趣很弱」会挤在一起看不出来。
 * min–max 映射保证全选「非常不符合」得 0 分、全选「非常符合」得 100 分。
 *
 * <h2>追溯口径</h2>
 * 每个维度的得分必须能说出是哪 5 道题的哪个选项加出来的（AC-1 第 2 条）。所以计分结果
 * 同时返回 {@code trace}，记录每个维度由哪些 {@code (题号, 选项)} 构成。
 * 若某维度的 trace 条数不等于 {@link HollandQuestionBank#QUESTIONS_PER_DIMENSION}，
 * 说明有分数找不到来源，这属于 AC-8 的 {@code SCORE_NOT_TRACEABLE}，那道分支在 T5 接。
 */
public final class HollandScorer {

    /** 单题作答，{@code option} 取值 1–4。 */
    public record ScoreItem(int questionId, int option) {
    }

    /**
     * @param scores 六维归一化分数，0–100
     * @param trace  每个维度的原始作答明细，用于 AC-1 的可追溯断言与 T5 的 AC-8 校验
     */
    public record Result(Map<HollandDimension, Integer> scores,
                         Map<HollandDimension, List<ScoreItem>> trace) {

        /** 某维度得分是否可完整回溯到 5 道题。T5 用它判 AC-8。 */
        public boolean isTraceable(HollandDimension dimension) {
            List<ScoreItem> items = trace.get(dimension);
            return items != null && items.size() == HollandQuestionBank.QUESTIONS_PER_DIMENSION;
        }

        public boolean allTraceable() {
            for (HollandDimension d : HollandDimension.values()) {
                if (!isTraceable(d)) {
                    return false;
                }
            }
            return true;
        }
    }

    private HollandScorer() {
    }

    /**
     * 对完整作答计分。
     *
     * @param answers 题号 → 选项，键必须覆盖 1–30 全部题目；不完整时由调用方先拦，
     *                这里不重复做 AC-6 的判定，避免两处各判一次后口径走岔
     */
    public static Result score(Map<Integer, Integer> answers) {
        Map<HollandDimension, Integer> raw = new EnumMap<>(HollandDimension.class);
        Map<HollandDimension, List<ScoreItem>> trace = new EnumMap<>(HollandDimension.class);
        for (HollandDimension d : HollandDimension.values()) {
            raw.put(d, 0);
            trace.put(d, new ArrayList<>());
        }

        for (Map.Entry<Integer, Integer> entry : answers.entrySet()) {
            int questionId = entry.getKey();
            int option = entry.getValue();
            HollandQuestion question = HollandQuestionBank.byId(questionId)
                    .orElseThrow(() -> new IllegalArgumentException("题号越界: " + questionId));
            if (!HollandQuestionBank.isLegalOption(option)) {
                throw new IllegalArgumentException("选项越界: " + option + " at question " + questionId);
            }
            raw.merge(question.dimension(), option, Integer::sum);
            trace.get(question.dimension()).add(new ScoreItem(questionId, option));
        }

        Map<HollandDimension, Integer> normalized = new EnumMap<>(HollandDimension.class);
        int min = HollandQuestionBank.QUESTIONS_PER_DIMENSION * HollandQuestionBank.OPTION_MIN;
        int max = HollandQuestionBank.QUESTIONS_PER_DIMENSION * HollandQuestionBank.OPTION_MAX;
        for (HollandDimension d : HollandDimension.values()) {
            int value = Math.round((raw.get(d) - min) * 100f / (max - min));
            normalized.put(d, Math.max(0, Math.min(100, value)));
        }

        // trace 按题号排序，让同一份作答每次得到同一顺序，便于比对与测试
        trace.values().forEach(items -> items.sort((a, b) -> Integer.compare(a.questionId(), b.questionId())));
        return new Result(normalized, trace);
    }
}
