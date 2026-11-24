package com.campus.career.resume;

import com.campus.career.common.BusinessException;
import com.campus.career.common.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

/**
 * 解析链路的编排层，对应 spec FR-4 / FR-5 / FR-8 与 architecture 4.1.1 的失败响应表。
 *
 * <p>一次解析走五步，顺序不能换：
 * <ol>
 *   <li><b>调模型，带单次超时。</b>超时阈值来自 {@link ParseRetryPolicy}，不是 30 秒写死在此；
 *   <li><b>Schema 校验。</b>模型可能返回自然语言，交 {@link ResumeParseSchema} 整份判定；
 *   <li><b>全空判定。</b>四类字段全空是 FR-5 的独立分支（{@code NO_VALID_FIELD}）；
 *   <li><b>写暂存区。</b>全链路唯一一次写，位置在全部校验之后；
 *   <li><b>记审计。</b>成功失败都要记，否则 NFR-3 的回溯能力在失败路径上就是空的。
 * </ol>
 *
 * <h2>重试口径</h2>
 * <p>只有<b>超时</b>会重试，其余失败一律终止。依据是 spec/module-01「边界与异常」表的写法：
 * 超时那行写「重试 2 次后放弃」，502 那行写「校验失败即终止，不展示半成品」，
 * 两行刻意对照，所以重试是超时独有的行为，不是所有失败的通用策略。
 * <ul>
 *   <li><b>超时</b>（504）进重试循环，最多共 3 次尝试，每次 30 秒、间隔 1 秒，
 *       最坏 92 秒后放弃；</li>
 *   <li><b>模型输出不合法</b>（502）<b>只调 1 次</b>：模型已经回过话了，再问一遍同类输出
 *       仍是不可用的，多等两轮只会让学生白等十几秒。这一条同时符合
 *       {@link ResumeParseModel} 接口注释里「只有超时才计入重试」的约定；</li>
 *   <li><b>四类字段全空</b>（422）<b>只调 1 次</b>：模型已正常返回，重试仍是空。
 *       它属于 FR-5 的结果判定，不是 FR-4 的调用失败。</li>
 * </ul>
 *
 * <h2>为什么失败时正式档案一定不受影响</h2>
 * <p>本类<b>不持有</b> {@code ConfirmedResumeRepository}，构造器里没有这个依赖。
 * 写不到的东西就不可能被写坏，比在失败分支里补一句「记得别动档案」可靠得多。
 *
 * <h2>调用本身失败怎么办</h2>
 * <p>网关 500、鉴权失败、连接超时这类异常没有独立错误码：M1 的六个码是 spec 与接口契约的
 * 封闭集合，自造 {@code LLM_ERROR} 属越界。按 502 {@code MODEL_OUTPUT_INVALID} 处理，
 * 502 的语义本就是「上游网关返回的东西不能用」。
 * <p>这类失败同样<b>不重试</b>。原因是口径要单一：502 在边界表里只有一种处置，
 * 若按失败来源分成「校验失败 1 次、网络失败 3 次」，同一状态码就有两种耗时表现，
 * 审计日志里的 {@code attempts} 也就没法作为 502 的判定依据。
 * 网络抖动的自愈放在网关侧更合适，不占学生的 92 秒。
 */
@Service
public class ResumeParseService {

    private final ResumeParseModel model;
    private final ParseRetryPolicy policy;
    private final ResumeStagingRepository staging;
    private final ParseAuditLog auditLog;

    /**
     * 承载单次模型调用的线程池，用来给每次调用加超时闸。
     *
     * <p>用无界缓存池而不复用单线程：模型若不响应中断（第三方 SDK 常有这种情况），
     * 单线程池会被一个卡死的任务堵住，后续请求全部排队。线程是 daemon，
     * 超时后 {@code cancel(true)} 打断，不死就让它自生自灭到 JVM 退出，不阻塞进程关闭。
     */
    private final ExecutorService gateway = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "resume-parse-gateway");
        thread.setDaemon(true);
        return thread;
    });

    public ResumeParseService(ResumeParseModel model,
                              ParseRetryPolicy policy,
                              ResumeStagingRepository staging,
                              ParseAuditLog auditLog) {
        this.model = model;
        this.policy = policy;
        this.staging = staging;
        this.auditLog = auditLog;
    }

    /**
     * 解析一份简历。
     *
     * @param studentId 学生标识，暂存区按它覆盖
     * @param document  简历字节
     * @param requestId 请求标识，与响应体里的是同一个，写进审计日志便于前后端对账
     * @return 四类字段，已是整份校验通过的结果
     * @throws BusinessException 失败时抛，错误码为 504 / 502 / 422 之一
     */
    public ParsedResume parse(String studentId, byte[] document, String requestId) {
        String fileId = nextFileId();
        Instant startedAt = Instant.now();
        long beginNanos = System.nanoTime();
        int attempts = 0;
        ErrorCode failure = null;

        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            if (attempt > 1) {
                pauseBeforeRetry();
            }
            attempts = attempt;
            try {
                ParsedResume parsed = ResumeParseSchema.validate(callModel(document));
                if (parsed.isEmpty()) {
                    // FR-5 的独立分支：调用成功、结果为空。不重试，直接结束。
                    failure = ErrorCode.NO_VALID_FIELD;
                    break;
                }
                // 全链路唯一的写点：到这一步为止，四类字段已经整份校验通过
                staging.replace(studentId, parsed);
                audit(requestId, studentId, fileId, startedAt, beginNanos, attempts, null);
                return parsed;
            } catch (TimeoutException e) {
                // 唯一会重试的失败：边界表「大模型连续三次均在 30 秒内无响应 → 重试 2 次后放弃」
                failure = ErrorCode.PARSE_TIMEOUT;
            } catch (BusinessException e) {
                // ResumeParseSchema 抛出的 MODEL_OUTPUT_INVALID。
                // 边界表「校验失败即终止，不展示半成品」——终止就是不再试，直接出循环。
                failure = e.errorCode();
                break;
            } catch (ModelCallFailedException e) {
                // 网关、鉴权、中断这类调用失败同样映射 502；口径与校验失败保持一致，1 次即终止
                failure = ErrorCode.MODEL_OUTPUT_INVALID;
                break;
            }
        }

        // maxAttempts() 恒 ≥ 1，循环必然至少跑一次并给 failure 赋值；兜底只为消除可空分支
        ErrorCode finalFailure = failure == null ? ErrorCode.MODEL_OUTPUT_INVALID : failure;
        audit(requestId, studentId, fileId, startedAt, beginNanos, attempts, finalFailure);
        throw new BusinessException(finalFailure);
    }

    /**
     * 调一次模型，超过单次超时阈值即放弃。
     *
     * <p>超时后必须 {@code cancel(true)}：不打断的话，一次超时会留下一个继续跑满整个
     * 模型调用时长的线程，重试三次就是三个，压测时线程数会翻倍。
     */
    private String callModel(byte[] document) throws TimeoutException {
        Future<String> future = gateway.submit(() -> model.parse(document));
        try {
            return future.get(policy.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ModelCallFailedException("等待解析结果时被中断", e);
        } catch (ExecutionException e) {
            throw new ModelCallFailedException("解析调用失败", e.getCause());
        }
    }

    /** 两次尝试之间等 1 秒（FR-4）。被打断时立刻结束，不吞中断标记。 */
    private void pauseBeforeRetry() {
        try {
            Thread.sleep(policy.interval().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelCallFailedException("重试等待被中断", e);
        }
    }

    private void audit(String requestId, String studentId, String fileId,
                       Instant startedAt, long beginNanos, int attempts, ErrorCode errorCode) {
        long totalMillis = Math.max(0L, (System.nanoTime() - beginNanos) / 1_000_000L);
        auditLog.record(new ParseTrace(requestId, studentId, fileId, startedAt, Instant.now(),
                totalMillis, attempts, attempts > 1, errorCode == null ? null : errorCode.name()));
    }

    /** 随机文件标识，替代原始文件名进日志（NFR-2、FR-3）。 */
    private String nextFileId() {
        return "f_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * 模型调用本身出了问题（网关不可用、鉴权失败、线程被打断）。
     *
     * <p>单独成形是为了不让它和 {@link BusinessException} 混在同一个 catch 里，
     * 也避免 {@code catch (RuntimeException)} 把真正的编码错误一并吞成 502。
     */
    private static final class ModelCallFailedException extends RuntimeException {

        ModelCallFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
