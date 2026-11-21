package com.campus.career.holland;

/**
 * 一道问卷题目。
 *
 * <p>题目内容与顺序固定，不随机抽题、不允许跳题（spec/module-02 FR-1）。
 * 选项是四档统一量表，不属于单题数据，见 {@link HollandQuestionBank#OPTIONS}。
 */
public record HollandQuestion(int id, String text, HollandDimension dimension) {
}
