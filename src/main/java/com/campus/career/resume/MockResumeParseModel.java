package com.campus.career.resume;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * {@link ResumeParseModel} 的 Mock 实现，本周边界内唯一的大模型替身。
 *
 * <h2>为什么是 Mock</h2>
 * <p>大模型网关与调用凭据不在本周的交付物里，按本周编码约束第 2 条
 * 「不得先于依赖开发业务逻辑，前置未完成只做契约骨架 + Mock」，
 * 这里先给一个可注入失败形态的替身，把<b>降级链路</b>（重试、超时、Schema 校验、
 * 失败零写入）先跑通并测透。真实实现接上后只需换掉这个 Bean，服务层与测试全部不动。
 *
 * <h2>为什么要能切换失败形态</h2>
 * <p>AC-6（超时）与 AC-7（自然语言、缺必填字段）是三条不同的失败路径，
 * 只有让替身能在运行时切换，才能在同一条上传端点上分别验到 504 与 502。
 * 切换是测试专用能力，生产环境不会有谁调 {@link #setMode}。
 *
 * <p>返回内容里没有任何真实个人信息，也不回显上传的文件内容，Mock 不参与 NFR-2 的隐私面。
 */
@Component
public class MockResumeParseModel implements ResumeParseModel {

    /** 模型输出的几种形态，对应 FR-4 的成功与两种失败分支。 */
    public enum Mode {
        /** 合法 JSON，四类字段齐全。 */
        VALID,
        /** 一直不返回，用于触发单次调用的 30 秒超时（AC-6）。 */
        TIMEOUT,
        /** 自然语言说明而不是 JSON，用于触发 AC-7 的第一种情形。 */
        NATURAL_LANGUAGE,
        /** 是 JSON 但教育背景缺必填键 {@code major}，用于触发 AC-7 的第二种情形。 */
        MISSING_FIELD,
        /** 合法 JSON，但项目与实习为空数组，用于验证 AC-4「空数组不省略字段」。 */
        PARTIAL,
        /** 合法 JSON 但四类字段全空，用于触发 AC-8 的 {@code NO_VALID_FIELD}。 */
        ALL_EMPTY
    }

    /** TIMEOUT 形态在未显式设置延时下的默认停留时长，远大于任何一次调用的超时阈值。 */
    private static final long DEFAULT_TIMEOUT_DELAY_MILLIS = 5_000L;

    private static final String VALID_JSON = """
            {
              "education": [
                {"school": "XX大学", "major": "软件工程", "degree": "本科",
                 "startDate": "2022-09", "endDate": "2026-06"}
              ],
              "skills": ["Java", "SQL"],
              "projects": [
                {"name": "校园二手交易", "role": "后端", "description": "负责订单与支付模块"}
              ],
              "internships": [
                {"company": "某科技有限公司", "role": "后端实习生", "description": "参与接口开发"}
              ]
            }
            """;

    private static final String NATURAL_LANGUAGE_TEXT =
            "这份简历整体不错，候选人有 Java 与 SQL 的使用经验，建议投递后端开发岗位。";

    private static final String MISSING_FIELD_JSON = """
            {
              "education": [
                {"school": "XX大学", "degree": "本科"}
              ],
              "skills": ["Java"],
              "projects": [],
              "internships": []
            }
            """;

    private static final String PARTIAL_JSON = """
            {
              "education": [
                {"school": "XX大学", "major": "软件工程", "degree": "本科"}
              ],
              "skills": ["Java"],
              "projects": [],
              "internships": []
            }
            """;

    private static final String ALL_EMPTY_JSON = """
            {"education": [], "skills": [], "projects": [], "internships": []}
            """;

    private volatile Mode mode = Mode.VALID;

    private volatile long delayMillis = 0L;

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public String parse(byte[] document) {
        calls.incrementAndGet();
        Mode current = mode;
        long delay = current == Mode.TIMEOUT && delayMillis <= 0L ? DEFAULT_TIMEOUT_DELAY_MILLIS : delayMillis;
        sleep(delay);
        return switch (current) {
            case VALID -> VALID_JSON;
            case TIMEOUT -> VALID_JSON;
            case NATURAL_LANGUAGE -> NATURAL_LANGUAGE_TEXT;
            case MISSING_FIELD -> MISSING_FIELD_JSON;
            case PARTIAL -> PARTIAL_JSON;
            case ALL_EMPTY -> ALL_EMPTY_JSON;
        };
    }

    /** 切换失败形态，测试用。 */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /** 设置每次调用的停留时长。TIMEOUT 形态下超过超时阈值即触发超时。 */
    public void setDelayMillis(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    /** 被真正调用了几次。服务层的重试是不是真的打到了模型，靠这个数核对。 */
    public int calls() {
        return calls.get();
    }

    /** 复位成默认成功形态并清空计数，测试用例之间互不污染。 */
    public void reset() {
        this.mode = Mode.VALID;
        this.delayMillis = 0L;
        this.calls.set(0);
    }

    /**
     * 用可中断的 sleep 模拟「模型迟迟不返回」。
     *
     * <p>必须响应中断：服务层在超时后会 {@code cancel(true)}，如果这里不可中断，
     * 每次超时都会留下一个跑满 5 秒的线程，用例一多就把线程池拖垮。
     */
    private void sleep(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("解析调用被中断", e);
        }
    }

}
