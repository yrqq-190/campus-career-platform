package com.campus.career.resume;

import java.time.Instant;

/**
 * 单次解析的可回溯记录，字段即 spec NFR-3 的清单，一个不多。
 *
 * <p>NFR-3 要求「记录 requestId、开始与结束时间、总耗时、是否发生重试、最终错误码」，
 * 本记录逐项对应。另外两项来自 NFR-2 的「只记录文件标识」与学生定位需要：
 * <ul>
 *   <li>{@code fileId}：随机文件标识，代替原始文件名。原始文件名常含姓名，
 *       写进日志就违反了 NFR-2 不记个人信息的要求（也呼应 FR-3「不使用原始文件名」）；
 *   <li>{@code studentId}：不透明标识，用于把一次失败定位到具体学生。它不是手机号、
 *       身份证号或简历正文，不属于 NFR-2 的禁记项。
 * </ul>
 *
 * <p><b>没有简历正文，也没有解析出来的字段值。</b>这是刻意的：本记录一旦带上内容，
 * 日志就成了简历副本，NFR-2 直接失守。所以它只能是元数据。
 *
 * @param requestId   请求标识，与响应体的 {@code requestId} 同一个值，方便前后端对账
 * @param studentId   学生标识
 * @param fileId      随机文件标识，非原始文件名
 * @param startedAt   本次解析开始时间
 * @param endedAt     本次解析结束时间
 * @param totalMillis 端到端总耗时，含所有重试与间隔
 * @param attempts    实际尝试次数，1 表示没有重试
 * @param retried     是否发生过重试
 * @param errorCode   最终错误码；成功时为 {@code null}
 */
public record ParseTrace(String requestId,
                         String studentId,
                         String fileId,
                         Instant startedAt,
                         Instant endedAt,
                         long totalMillis,
                         int attempts,
                         boolean retried,
                         String errorCode) {

    /** 成功的记录用 OK 落日志，避免出现 {@code errorCode=null} 这种读起来像缺字段的行。 */
    public String errorCodeForLog() {
        return errorCode == null ? "OK" : errorCode;
    }
}
