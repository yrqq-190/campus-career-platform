package com.campus.career.holland.dto;

import com.campus.career.holland.HollandDimension;
import com.campus.career.holland.HollandQuestion;
import com.campus.career.holland.HollandQuestionBank;
import java.util.List;

/**
 * 单题视图，对应 architecture 4.2.1 响应里的 {@code questions} 元素。
 *
 * <p>选项是统一四档量表，每道题都一样，所以不从题目数据里逐个存，归一到题库常量。
 */
public record QuestionView(int id, String text, HollandDimension dimension, List<String> options) {

    public static QuestionView of(HollandQuestion question) {
        return new QuestionView(question.id(), question.text(), question.dimension(),
                HollandQuestionBank.OPTIONS);
    }
}
