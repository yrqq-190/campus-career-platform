package com.campus.career.holland.profile;

/**
 * 某维度得分无法回溯到原始作答（AC-8）。
 *
 * <p>触发条件：计分结果的 trace 里某个维度的题数不等于题库规定的 5 题，
 * 说明这个分数没有完整来源。这时该次画像不能对外提供，也不能只展示部分正确的分数
 * （边界行 6）。
 */
public class ScoreNotTraceableException extends RuntimeException {

    public ScoreNotTraceableException(String message) {
        super(message);
    }
}
