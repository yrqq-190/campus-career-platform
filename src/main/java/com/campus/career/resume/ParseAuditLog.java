package com.campus.career.resume;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 解析链路的审计日志，落在 spec NFR-2 与 NFR-3 上。
 *
 * <p>两条要求一起管，是因为它们拧在同一个地方：NFR-3 要「记得够多」才可回溯，
 * NFR-2 要「记得够少」才不泄露。所以这里只写白名单字段（{@link ParseTrace}），
 * 而不是把整个请求对象打出来再靠人肉删减。
 *
 * <p>日志行里出现的值只有：requestId、studentId、fileId、起止时间、耗时、尝试次数、
 * 是否重试、错误码。<b>不出现</b>简历正文、原始文件名、手机号、身份证号、模型输出。
 *
 * <p>内存里的 {@link #traces()} 是本周占位，让测试能断言「记录确实写下了这几项」；
 * T1 建表后改成落审计表，日志保留策略交给运维。
 */
@Component
public class ParseAuditLog {

    private static final Logger log = LoggerFactory.getLogger(ParseAuditLog.class);

    private final List<ParseTrace> traces = new CopyOnWriteArrayList<>();

    /** 记录一次解析。成功与失败走同一个方法，避免失败路径漏记。 */
    public void record(ParseTrace trace) {
        traces.add(trace);
        // NFR-2：只记标识、时间、耗时、重试与错误码，不记任何简历内容
        log.info("简历解析结束 requestId={} studentId={} fileId={} startedAt={} endedAt={} totalMs={} "
                        + "retried={} attempts={} errorCode={}",
                trace.requestId(), trace.studentId(), trace.fileId(), trace.startedAt(), trace.endedAt(),
                trace.totalMillis(), trace.retried(), trace.attempts(), trace.errorCodeForLog());
    }

    /** 已记录的解析轨迹，测试与排查用。 */
    public List<ParseTrace> traces() {
        return List.copyOf(traces);
    }

    /** 清空记录，测试用例之间隔离用。 */
    public void clear() {
        traces.clear();
    }
}
