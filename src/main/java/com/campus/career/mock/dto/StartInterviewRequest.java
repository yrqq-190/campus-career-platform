package com.campus.career.mock.dto;

/**
 * 开始面试请求，对应 architecture 4.5.1。
 *
 * <p>两个字段都可能为空，<b>不在这一层做 Bean Validation</b>：契约 4.5.1 失败表把这个判定写成
 * {@code 400 + JD_REQUIRED}，是带业务码的业务判定，归服务层；Bean Validation 的 400 无业务码，
 * 两者响应体不一样，混在一起前端没法区分。
 *
 * @param jobId  投递记录 ID，M4 未接入前可空
 * @param jdText 手填 JD，降级路径下必填且不少于 50 字（FR-1、AC-4）
 */
public record StartInterviewRequest(String jobId, String jdText) {
}
