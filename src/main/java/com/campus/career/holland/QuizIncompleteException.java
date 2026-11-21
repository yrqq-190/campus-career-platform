package com.campus.career.holland;

import java.util.List;

/**
 * 30 题未答完就提交（AC-6）。
 *
 * <p>单独一个异常类型，是因为 422 的响应体比其他错误多一个顶层 {@code missing} 数组
 * （architecture 4.2.3 明确要求带未答题号），统一响应体 {@code ApiResponse} 装不下。
 * 若为此给 ApiResponse 加字段，会让 M1 的每个响应都多出一个空数组。
 */
public class QuizIncompleteException extends RuntimeException {

    private final transient List<Integer> missing;

    public QuizIncompleteException(String message, List<Integer> missing) {
        super(message);
        this.missing = List.copyOf(missing);
    }

    public List<Integer> missing() {
        return missing;
    }
}
