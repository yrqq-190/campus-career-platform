package com.campus.career.resume;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 解析调用的超时与重试参数，逐字对应 spec FR-4 与 architecture 4.1.1。
 *
 * <p>三组数字是 spec 明文写死的，不是可调优参数：
 * 超时 30 秒、失败最多重试 2 次（即最多 3 次尝试）、两次重试间隔 1 秒。
 * 最坏耗时 30 × 3 + 1 × 2 = 92 秒，与 NFR-1 给出的数字一致。
 *
 * <h2>为什么做成可配置</h2>
 * 唯一目的是让测试能在毫秒级验完重试逻辑。真按 30 秒跑三次的用例要 92 秒，
 * 进不了 CI。生产配置写在 {@code application.yml}，取值与 FR-4 一致；
 * {@link #defaults()} 是同一组数字的代码形态，用于断言 92 秒这个常量没被改坏。
 *
 * <p>要改这三组数字，先改 spec 的 FR-4，再回来改这里，不允许只改配置。
 */
@Component
public class ParseRetryPolicy {

    /** FR-4：调用超时固定 30 秒。 */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /** FR-4：失败最多重试 2 次。 */
    public static final int DEFAULT_RETRIES = 2;

    /** FR-4：两次重试间隔 1 秒。 */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(1);

    private final Duration timeout;
    private final int retries;
    private final Duration interval;

    public ParseRetryPolicy(@Value("${career.parse.timeout:30s}") Duration timeout,
                            @Value("${career.parse.retries:2}") int retries,
                            @Value("${career.parse.interval:1s}") Duration interval) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("解析超时必须为正数");
        }
        if (retries < 0) {
            throw new IllegalArgumentException("重试次数不能为负数");
        }
        if (interval == null || interval.isNegative()) {
            throw new IllegalArgumentException("重试间隔不能为负数");
        }
        this.timeout = timeout;
        this.retries = retries;
        this.interval = interval;
    }

    /** spec FR-4 的原始参数，用作「92 秒」这条断言的唯一来源。 */
    public static ParseRetryPolicy defaults() {
        return new ParseRetryPolicy(DEFAULT_TIMEOUT, DEFAULT_RETRIES, DEFAULT_INTERVAL);
    }

    /** 单次调用允许等待的时长。 */
    public Duration timeout() {
        return timeout;
    }

    /** 失败后额外重试的次数。 */
    public int retries() {
        return retries;
    }

    /** 最多尝试次数，等于首次加重试次数。 */
    public int maxAttempts() {
        return retries + 1;
    }

    /** 两次重试之间的等待时长。 */
    public Duration interval() {
        return interval;
    }

    /**
     * 最坏耗时：每次都跑满超时，且每次都走完重试。
     *
     * <p>NFR-1 与 architecture 4.1.1 都引用了这个数（92 秒），
     * 前端据此决定 loading 至少要保持多久、阶段文案怎么推进。
     */
    public long worstCaseMillis() {
        return timeout.toMillis() * maxAttempts() + interval.toMillis() * retries;
    }
}
