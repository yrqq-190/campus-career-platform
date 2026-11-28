package com.campus.career.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 关键词四维评分（FR-5），<b>不调用大模型</b>（spec Non-Goals 明写不做实时题生成）。
 *
 * <p>评分口径全部是规则，可复现、可单测：
 * <ol>
 *   <li>按题目维度取该维度的关键词表，统计作答文本命中的词数（英文不区分大小写）；</li>
 *   <li>命中率 = min(命中数 / 3, 1.0)，即命中 3 个词就拿满关键词分；</li>
 *   <li>篇幅率 = min(字数 / 50, 1.0)，50 字以上拿满篇幅分；</li>
 *   <li>得分 = round(100 × (0.7 × 命中率 + 0.3 × 篇幅率))，裁剪到 0–100 的整数。</li>
 * </ol>
 *
 * <p>{@code scoreStatus} 的判定（AC-5、边界表「评分维度缺项」）：
 * 字数少于 {@link #MIN_MEANING_LENGTH} 记为极短回答，或一个关键词都没命中，都标 {@code PARTIAL}；
 * 两者都不满足才是 {@code COMPLETE}。标 {@code PARTIAL} 不等于 0 分——边界表要求的是「给出低分 + 提示」，
 * 不是判负，所以仍按上面公式出分，只是状态与提示不同。
 */
@Component
public class MockAnswerScorer {

    /** 四个维度，取值与 architecture 4.5.1 / 4.5.2 的 dimension 字符串完全一致（FR-5）。 */
    public static final String DIMENSION_PROFESSIONAL = "专业知识";
    public static final String DIMENSION_LOGIC = "逻辑表达";
    public static final String DIMENSION_EXPERIENCE = "项目经验";
    public static final String DIMENSION_MATCH = "岗位匹配度";

    /** 少于这个字数即视为「极短/敷衍」（AC-5）。取 10 是因为「不知道」「没做过」这类敷衍回答都在 5 字以内。 */
    static final int MIN_MEANING_LENGTH = 10;

    /** 期望篇幅。取 50 与 spec 边界表「JD 少于 50 字需提示」同口径，学生对这个数量级有直觉。 */
    static final int RECOMMENDED_LENGTH = 50;

    /** 命中满分的词数。3 个是「能说出具体做法」的最低门槛，再多不加分，避免堆词刷分。 */
    private static final int FULL_HIT_COUNT = 3;

    private static final double KEYWORD_WEIGHT = 0.7;
    private static final double LENGTH_WEIGHT = 0.3;

    /**
     * 四维关键词表。
     *
     * <p>这是 MVP1 的 Mock 词表，后续接题库/岗位词典时替换本表即可，评分公式与状态判定不用动。
     * 词表按「这个维度答得像不像」选词，而不是按技术栈穷举：专业知识看技术名词，
     * 逻辑表达看结构与因果，项目经验看场景与动作，岗位匹配度看业务与协作。
     */
    private static final Map<String, List<String>> KEYWORDS = buildKeywords();

    public static final String STATUS_COMPLETE = "COMPLETE";
    public static final String STATUS_PARTIAL = "PARTIAL";

    /**
     * 对一次作答评分。
     *
     * @param dimension  题目所属维度，取四个常量之一
     * @param answerText 学生作答原文，允许为空（空串按极短回答处理，不抛异常）
     */
    public MockScore score(String dimension, String answerText) {
        String text = answerText == null ? "" : answerText.trim();
        List<String> vocabulary = KEYWORDS.getOrDefault(dimension, List.of());
        List<String> hits = matchKeywords(text, vocabulary);

        boolean tooShort = text.length() < MIN_MEANING_LENGTH;
        boolean noHit = hits.isEmpty();

        double keywordRatio = Math.min(1.0, hits.size() / (double) FULL_HIT_COUNT);
        double lengthRatio = Math.min(1.0, text.length() / (double) RECOMMENDED_LENGTH);
        int score = (int) Math.round(100 * (KEYWORD_WEIGHT * keywordRatio + LENGTH_WEIGHT * lengthRatio));
        score = Math.max(0, Math.min(100, score));

        String status = (tooShort || noHit) ? STATUS_PARTIAL : STATUS_COMPLETE;
        return new MockScore(score, status, feedback(text, hits, tooShort, noHit));
    }

    private List<String> matchKeywords(String text, List<String> vocabulary) {
        String lower = text.toLowerCase();
        List<String> hits = new ArrayList<>();
        for (String keyword : vocabulary) {
            if (lower.contains(keyword.toLowerCase())) {
                hits.add(keyword);
            }
        }
        return hits;
    }

    private String feedback(String text, List<String> hits, boolean tooShort, boolean noHit) {
        if (tooShort) {
            // 文案取自 spec 边界表「回答极短/敷衍」：回答过于简短，建议展开描述
            return "回答过于简短，建议展开描述（当前 " + text.length()
                    + " 字，建议不少于 " + RECOMMENDED_LENGTH + " 字）。";
        }
        if (noHit) {
            return "未命中该维度关键词，建议补充具体场景、做法与结果。";
        }
        return "命中关键词：" + String.join("、", hits) + "。";
    }

    private static Map<String, List<String>> buildKeywords() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put(DIMENSION_PROFESSIONAL, List.of(
                "Java", "Spring", "MySQL", "Redis", "数据库", "索引", "缓存", "线程", "事务",
                "接口", "并发", "性能", "优化", "算法", "框架", "测试"));
        map.put(DIMENSION_LOGIC, List.of(
                "首先", "其次", "然后", "最后", "因为", "所以", "因此", "但是", "如果",
                "背景", "目标", "方案", "结果", "总结", "复盘"));
        map.put(DIMENSION_EXPERIENCE, List.of(
                "项目", "负责", "上线", "故障", "排查", "日志", "定位", "超时", "回滚",
                "扩容", "监控", "压测", "协作", "交付", "迭代"));
        map.put(DIMENSION_MATCH, List.of(
                "业务", "客户", "需求", "团队", "沟通", "推进", "落地", "规划", "岗位",
                "目标", "成长", "学习"));
        return Map.copyOf(map);
    }

    /**
     * 单题评分结果。
     *
     * @param score       0–100 整数
     * @param scoreStatus {@code COMPLETE} 或 {@code PARTIAL}（AC-5）
     * @param feedback    命中关键词或「过于简短」的提示，直接回给前端展示
     */
    public record MockScore(int score, String scoreStatus, String feedback) {
    }
}
