package com.campus.career.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.career.common.BusinessException;
import com.campus.career.common.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 解析链路的重试、降级与零写入，对应 AC-6 / AC-7 / AC-8 / AC-9 与 NFR-3。
 *
 * <p>不起 Spring 上下文：这里验的是服务层逻辑本身，参数换成毫秒级（超时 40ms、间隔 5ms），
 * 一个用例几十毫秒跑完。真实配置的 92 秒口径由 {@link ParseRetryPolicyTest} 单独钉住。
 *
 * <p>「零写入」的验法不是看返回值，而是直接读暂存区：失败后暂存区要么为空、
 * 要么还是上一次成功的那份内容，绝不出现半份结果。
 */
class ResumeParseServiceTest {

    private static final Duration TIMEOUT = Duration.ofMillis(40);
    private static final Duration INTERVAL = Duration.ofMillis(5);
    private static final String STUDENT = "svc-student";
    private static final byte[] DOCUMENT = "%PDF-1.7 mock".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private MockResumeParseModel model;
    private InMemoryResumeStagingRepository staging;
    private ParseAuditLog auditLog;
    private ResumeParseService service;

    @BeforeEach
    void setUp() {
        model = new MockResumeParseModel();
        staging = new InMemoryResumeStagingRepository();
        auditLog = new ParseAuditLog();
        service = new ResumeParseService(model,
                new ParseRetryPolicy(TIMEOUT, 2, INTERVAL), staging, auditLog);
    }

    @Test
    @DisplayName("成功：四类字段写入暂存区，只调用一次模型，审计记 OK")
    void successWritesStaging() {
        ParsedResume parsed = service.parse(STUDENT, DOCUMENT, "req-ok");

        assertEquals(1, parsed.education().size());
        assertEquals(2, parsed.skills().size());
        assertTrue(staging.find(STUDENT).isPresent(), "解析成功后暂存区必须有内容");

        ParseTrace trace = onlyTrace();
        assertEquals(1, trace.attempts());
        assertFalse(trace.retried(), "一次就成功不应记成发生过重试");
        assertNull(trace.errorCode());
        assertEquals("req-ok", trace.requestId());
        assertTrue(trace.fileId().startsWith("f_"), "日志里只能是随机文件标识，不能是原始文件名");
    }

    @Test
    @DisplayName("AC-6：连续三次超时后放弃，返回 504，且暂存区为空")
    void timeoutAfterThreeAttempts() {
        model.setMode(MockResumeParseModel.Mode.TIMEOUT);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.parse(STUDENT, DOCUMENT, "req-timeout"));

        assertEquals(ErrorCode.PARSE_TIMEOUT, ex.errorCode());
        assertEquals("解析超时，请稍后重试", ex.getMessage(), "提示文案要与 AC-6 逐字一致");
        assertEquals(3, model.calls(), "首次加 2 次重试，模型必须被真正调用 3 次");
        assertTrue(staging.find(STUDENT).isEmpty(), "超时失败不得留下任何暂存内容");

        ParseTrace trace = onlyTrace();
        assertEquals(3, trace.attempts());
        assertTrue(trace.retried());
        assertEquals("PARSE_TIMEOUT", trace.errorCode());
    }

    @Test
    @DisplayName("NFR-3：超时用例的总耗时覆盖三次超时与两次间隔，不是只跑了最后一次")
    void timeoutTraceRecordsRealElapsedTime() {
        model.setMode(MockResumeParseModel.Mode.TIMEOUT);

        assertThrows(BusinessException.class, () -> service.parse(STUDENT, DOCUMENT, "req-elapsed"));

        long lowerBound = TIMEOUT.toMillis() * 3 + INTERVAL.toMillis() * 2;
        assertTrue(onlyTrace().totalMillis() >= lowerBound,
                "总耗时至少要等于三次超时加两次间隔，否则说明某次尝试被跳过了");
    }

    @Test
    @DisplayName("AC-7：模型返回自然语言，校验失败即终止，返回 502 且只调 1 次")
    void naturalLanguageBecomes502() {
        model.setMode(MockResumeParseModel.Mode.NATURAL_LANGUAGE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.parse(STUDENT, DOCUMENT, "req-natural"));

        assertEquals(ErrorCode.MODEL_OUTPUT_INVALID, ex.errorCode());
        assertEquals(1, model.calls(), "边界表「校验失败即终止」：502 不重试，只调 1 次");
        assertTrue(staging.find(STUDENT).isEmpty());

        ParseTrace trace = onlyTrace();
        assertEquals(1, trace.attempts());
        assertFalse(trace.retried());
        assertEquals("MODEL_OUTPUT_INVALID", trace.errorCode());
    }

    @Test
    @DisplayName("AC-7：JSON 缺必填字段，返回 502")
    void missingFieldBecomes502() {
        model.setMode(MockResumeParseModel.Mode.MISSING_FIELD);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.parse(STUDENT, DOCUMENT, "req-missing"));

        assertEquals(ErrorCode.MODEL_OUTPUT_INVALID, ex.errorCode());
        assertTrue(staging.find(STUDENT).isEmpty());
    }

    @Test
    @DisplayName("AC-8：解析成功但四类字段全空，返回 422 且不重试")
    void allEmptyBecomes422WithoutRetry() {
        model.setMode(MockResumeParseModel.Mode.ALL_EMPTY);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.parse(STUDENT, DOCUMENT, "req-empty"));

        assertEquals(ErrorCode.NO_VALID_FIELD, ex.errorCode());
        assertEquals(1, model.calls(), "结果为空不是调用失败，重试也是空，不该让学生多等两轮");

        ParseTrace trace = onlyTrace();
        assertEquals(1, trace.attempts());
        assertFalse(trace.retried());
        assertTrue(staging.find(STUDENT).isEmpty(), "不得生成空档案");
    }

    @Test
    @DisplayName("AC-9：已有暂存内容时再次上传失败，旧内容原样保留，不出现半份结果")
    void failureKeepsPreviousStaging() {
        ParsedResume first = service.parse(STUDENT, DOCUMENT, "req-first");
        model.setMode(MockResumeParseModel.Mode.TIMEOUT);

        assertThrows(BusinessException.class, () -> service.parse(STUDENT, DOCUMENT, "req-second"));

        ParsedResume stillThere = staging.find(STUDENT).orElseThrow();
        assertEquals(first, stillThere, "失败的上传不得覆盖、也不得清空上一份待确认结果");
    }

    @Test
    @DisplayName("AC-4：项目与实习为空数组时仍正常返回 201 的内容，不因空数组判失败")
    void partialEmptyIsNotFailure() {
        model.setMode(MockResumeParseModel.Mode.PARTIAL);

        ParsedResume parsed = service.parse(STUDENT, DOCUMENT, "req-partial");

        assertEquals(0, parsed.projects().size());
        assertEquals(0, parsed.internships().size());
        assertEquals(1, parsed.education().size());
    }

    @Test
    @DisplayName("NFR-3：成功与失败各记一条，轨迹里不含简历正文与个人信息")
    void auditHasNoPersonalContent() {
        service.parse(STUDENT, DOCUMENT, "req-a");
        model.setMode(MockResumeParseModel.Mode.TIMEOUT);
        assertThrows(BusinessException.class, () -> service.parse(STUDENT, DOCUMENT, "req-b"));

        List<ParseTrace> traces = auditLog.traces();
        assertEquals(2, traces.size());
        // 记录里只有元数据，没有字段值：这条断言盯的是「将来有人往 trace 里加 document 字段」
        assertFalse(traces.toString().contains("XX大学"));
        assertFalse(traces.toString().contains("Java"));
    }

    private ParseTrace onlyTrace() {
        List<ParseTrace> traces = auditLog.traces();
        assertEquals(1, traces.size(), "一次解析只应写一条轨迹");
        return traces.get(0);
    }
}
