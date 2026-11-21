package com.campus.career.resume;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.campus.career.holland.profile.ArchiveResumeProfileSource;
import com.campus.career.holland.profile.ResumeProfileSource;
import com.campus.career.holland.profile.ResumeSnapshot;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 解析失败三条分支的端点级验证，对应 AC-6 / AC-7 / AC-9、附带检查项 AC-8 与 NFR-2 / NFR-3。
 *
 * <p>与 {@link ResumeParseServiceTest} 的分工：那边验服务层逻辑，这边验「走完 HTTP 之后
 * 前端拿到的状态码、错误码、文案与字段数」，是 PR 评审真正要看的证据。
 *
 * <p>三件事在这一个类里同时验，是因为它们共用同一次失败请求：
 * 返回了什么（504/502）、档案动没动（AC-9）、日志记了什么（NFR-2/3）。
 * 拆成三个类就要把同一个场景跑三遍，也得写三份同样的准备代码。
 *
 * <p>超时与间隔换成毫秒级（60ms / 5ms），一个用例两百毫秒左右。
 * 92 秒这个数字由 {@link ParseRetryPolicyTest} 单独钉住，不靠真等。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "career.parse.timeout=60ms",
        "career.parse.interval=5ms",
        "career.parse.retries=2"
})
class ResumeParseFallbackControllerTest {

    private static final String H = "X-Student-Id";
    private static final String PATH = "/api/v1/resumes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockResumeParseModel model;

    @Autowired
    private InMemoryResumeStagingRepository staging;

    @Autowired
    private InMemoryConfirmedResumeRepository confirmed;

    @Autowired
    private ParseAuditLog auditLog;

    @BeforeEach
    void setUp() {
        model.reset();
        auditLog.clear();
    }

    @Test
    @DisplayName("AC-6：三次超时后返回 504 PARSE_TIMEOUT 与「解析超时，请稍后重试」")
    void ac6Timeout() throws Exception {
        String student = "ac6-student";
        model.setMode(MockResumeParseModel.Mode.TIMEOUT);

        mockMvc.perform(multipart(PATH).file(pdf("resume.pdf")).header(H, student))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PARSE_TIMEOUT"))
                .andExpect(jsonPath("$.message").value("解析超时，请稍后重试"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                // AC-6 的「不展示任何字段」：失败响应里没有 data
                .andExpect(jsonPath("$.data").doesNotExist());

        assertEquals(3, model.calls(), "必须是首次加 2 次重试共 3 次调用");
        assertTrue(staging.find(student).isEmpty(), "超时失败不得写暂存区");
        assertTrue(confirmed.find(student).isEmpty(), "更不得碰正式档案");

        ParseTrace trace = lastTrace();
        assertEquals(3, trace.attempts());
        assertTrue(trace.retried(), "NFR-3 要求记录是否发生重试");
        assertEquals("PARSE_TIMEOUT", trace.errorCode());
    }

    @Test
    @DisplayName("AC-7：模型返回自然语言说明，返回 502 MODEL_OUTPUT_INVALID")
    void ac7NaturalLanguage() throws Exception {
        String student = "ac7-natural";
        model.setMode(MockResumeParseModel.Mode.NATURAL_LANGUAGE);

        mockMvc.perform(multipart(PATH).file(pdf("resume.pdf")).header(H, student))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MODEL_OUTPUT_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertEquals(3, model.calls(), "FR-4：输出不合法也按「失败最多重试 2 次」处理");
        assertTrue(staging.find(student).isEmpty());
    }

    @Test
    @DisplayName("AC-7：模型输出缺必填字段，返回 502 MODEL_OUTPUT_INVALID")
    void ac7MissingRequiredField() throws Exception {
        String student = "ac7-missing";
        model.setMode(MockResumeParseModel.Mode.MISSING_FIELD);

        mockMvc.perform(multipart(PATH).file(pdf("resume.pdf")).header(H, student))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MODEL_OUTPUT_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertTrue(staging.find(student).isEmpty(), "不得把非结构化文本当作解析结果入库");
    }

    @Test
    @DisplayName("AC-8（附带检查项）：解析成功但四类字段全空，返回 422 NO_VALID_FIELD")
    void ac8NoValidField() throws Exception {
        String student = "ac8-student";
        model.setMode(MockResumeParseModel.Mode.ALL_EMPTY);

        mockMvc.perform(multipart(PATH).file(pdf("resume.pdf")).header(H, student))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_VALID_FIELD"))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertTrue(staging.find(student).isEmpty(), "不生成空档案");
    }

    @Test
    @DisplayName("AC-4：项目与实习为空数组时仍返回 201，字段存在且为空数组")
    void ac4EmptyCategoriesReturned() throws Exception {
        String student = "ac4-student";
        model.setMode(MockResumeParseModel.Mode.PARTIAL);

        mockMvc.perform(multipart(PATH).file(pdf("resume.pdf")).header(H, student))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parse.projects").isArray())
                .andExpect(jsonPath("$.data.parse.projects").isEmpty())
                .andExpect(jsonPath("$.data.parse.internships").isArray())
                .andExpect(jsonPath("$.data.parse.internships").isEmpty())
                .andExpect(jsonPath("$.data.parse.education[0].school").isNotEmpty());
    }

    @Test
    @DisplayName("AC-9：已有档案 A（3 段实习）时上传失败，档案 A 与画像读数都不变")
    void ac9ArchiveUntouched() throws Exception {
        String student = "ac9-student";
        ParsedResume contentA = new ParsedResume(
                List.of(Map.of("school", "XX大学", "major", "软件工程", "degree", "本科")),
                List.of("Java", "SQL"),
                List.of(Map.of("name", "校园二手交易", "role", "后端")),
                List.of(Map.of("company", "甲公司"), Map.of("company", "乙公司"), Map.of("company", "丙公司")));
        confirmed.replace(student, ConfirmedResume.of(contentA));
        model.setMode(MockResumeParseModel.Mode.TIMEOUT);

        mockMvc.perform(multipart(PATH).file(pdf("resume.pdf")).header(H, student))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PARSE_TIMEOUT"));

        ConfirmedResume after = confirmed.find(student).orElseThrow();
        assertEquals(3, after.content().internships().size(), "档案 A 的 3 段实习必须原样在");
        assertEquals(contentA, after.content(), "档案 A 的其他字段也必须原样");
        assertEquals(ConfirmedResume.CONFIRMED, after.status(),
                "确认状态不得被改成待确认或空态");
        // 口径说明第 2 条：confirmedAt 不作为通过条件，这里不断言它

        ResumeProfileSource portraitSide = new ArchiveResumeProfileSource(confirmed);
        ResumeSnapshot snapshot = portraitSide.findConfirmed(student).orElseThrow();
        assertEquals(3, snapshot.internships().size(),
                "画像模块读到的必须仍是档案 A，不是部分写入的中间态");
        assertEquals(List.of("Java", "SQL"), snapshot.skills());

        assertTrue(staging.find(student).isEmpty(), "失败链路上暂存区不得留下任何内容");
    }

    @Test
    @DisplayName("NFR-2：失败日志只记 requestId、耗时与错误码，不记姓名、手机号、身份证号与原始文件名")
    void nfr2LogHasNoPersonalContent() throws Exception {
        String student = "nfr2-student";
        model.setMode(MockResumeParseModel.Mode.TIMEOUT);

        Logger logger = (Logger) LoggerFactory.getLogger(ParseAuditLog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MvcResult result = mockMvc.perform(multipart(PATH)
                            .file(pdfWithText("张三 13800138000 110101199001011234", "张三-简历.pdf"))
                            .header(H, student))
                    .andExpect(status().isGatewayTimeout())
                    .andReturn();

            String requestId = JsonPath.read(result.getResponse().getContentAsString(), "$.requestId");
            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(joining("\n"));

            assertTrue(logged.contains(requestId), "日志必须带 requestId，否则前端报错对不上账");
            assertTrue(logged.contains("PARSE_TIMEOUT"), "NFR-3：必须记下最终错误码");
            assertTrue(logged.contains("retried=true"), "NFR-3：必须记下是否重试");
            assertFalse(logged.contains("13800138000"), "NFR-2：手机号不得进日志");
            assertFalse(logged.contains("110101199001011234"), "NFR-2：身份证号不得进日志");
            assertFalse(logged.contains("张三"), "NFR-2：姓名与原始文件名都不得进日志");
            assertFalse(logged.contains("张三-简历.pdf"), "FR-3：日志里只能用随机文件标识");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("NFR-3：每次解析都留一条轨迹，失败也要留")
    void nfr3EveryParseIsTraced() throws Exception {
        model.setMode(MockResumeParseModel.Mode.MISSING_FIELD);

        mockMvc.perform(multipart(PATH).file(pdf("resume.pdf")).header(H, "nfr3-student"))
                .andExpect(status().isBadGateway());

        ParseTrace trace = lastTrace();
        assertFalse(trace.requestId() == null || trace.requestId().isBlank(), "NFR-3 要求记 requestId");
        assertTrue(trace.startedAt() != null, "NFR-3 要求记开始时间");
        assertTrue(trace.endedAt() != null, "NFR-3 要求记结束时间");
        assertTrue(trace.totalMillis() >= 0L, "NFR-3 要求记总耗时");
        assertEquals("MODEL_OUTPUT_INVALID", trace.errorCode());
    }

    private ParseTrace lastTrace() {
        List<ParseTrace> traces = auditLog.traces();
        assertFalse(traces.isEmpty(), "解析结束必须留下一条轨迹（NFR-3）");
        return traces.get(traces.size() - 1);
    }

    private MockMultipartFile pdf(String fileName) {
        return pdfWithText("", fileName);
    }

    /** 造一份「文件头是 PDF、正文里带个人信息」的简历，用于验证日志不会把正文带出去。 */
    private MockMultipartFile pdfWithText(String text, String fileName) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[4 + body.length];
        bytes[0] = 0x25;
        bytes[1] = 0x50;
        bytes[2] = 0x44;
        bytes[3] = 0x46;
        System.arraycopy(body, 0, bytes, 4, body.length);
        return new MockMultipartFile("file", fileName, "application/pdf", bytes);
    }
}
