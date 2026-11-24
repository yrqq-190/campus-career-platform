package com.campus.career.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 重试参数的口径测试。
 *
 * <p>存在的理由只有一个：92 秒是 NFR-1、AC-6 与 architecture 4.1.1 三处引用的数字，
 * 而重试逻辑跑一遍要 92 秒，没法在 CI 里真等。所以把「参数本身没被改坏」单独钉成断言，
 * 业务链路的三种失败形态另用毫秒级配置验（{@link ResumeParseServiceTest}）。
 */
class ParseRetryPolicyTest {

    @Test
    @DisplayName("FR-4：超时 30 秒、重试 2 次、间隔 1 秒，最多 3 次尝试")
    void defaultsMatchSpec() {
        ParseRetryPolicy policy = ParseRetryPolicy.defaults();

        assertEquals(30_000L, policy.timeout().toMillis(), "超时必须是 30 秒");
        assertEquals(2, policy.retries(), "重试次数必须是 2");
        assertEquals(3, policy.maxAttempts(), "首次加 2 次重试共 3 次尝试");
        assertEquals(1_000L, policy.interval().toMillis(), "重试间隔必须是 1 秒");
    }

    @Test
    @DisplayName("NFR-1 / AC-6：最坏耗时 92 秒，即 30 × 3 + 1 × 2")
    void worstCaseIs92Seconds() {
        assertEquals(92_000L, ParseRetryPolicy.defaults().worstCaseMillis(),
                "把超时写成 32 秒、或漏算两次重试间隔，都会让前端 loading 的时长承诺失准");
    }

    @Test
    @DisplayName("非法的超时、重试次数、间隔在构造时就拒绝，不留到运行期")
    void rejectsIllegalArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParseRetryPolicy(Duration.ZERO, 2, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ParseRetryPolicy(Duration.ofSeconds(30), -1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ParseRetryPolicy(Duration.ofSeconds(30), 2, Duration.ofSeconds(-1)));
    }
}
