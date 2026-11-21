package com.campus.career.holland.dto;

/**
 * 单题保存响应，对应 architecture 4.2.2。
 *
 * @param saved         恒为 true，保存失败会走异常分支而不是这里
 * @param answeredCount 保存后的累计已作答题数，前端拿它更新进度
 */
public record SaveAnswerResponse(boolean saved, int answeredCount) {
}
