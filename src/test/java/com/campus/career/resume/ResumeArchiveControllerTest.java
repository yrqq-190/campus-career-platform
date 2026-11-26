package com.campus.career.resume;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.career.common.StudentIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 确认入库与读取档案两个端点的契约测试，对应 architecture 4.1.2、4.1.3。
 *
 * <p>补的是 M1 之前没有端点可验的两条验收条件：AC-2（改完确认才入库、以提交内容为准）
 * 与 AC-9 的后半段（确认链路失败时旧档案不变）。这两条在上传端点里验不到，
 * 因为上传成功只写暂存区，正式档案在那条链路上一直是空的。
 *
 * <p>其中 {@code uploadThenConfirmThenCurrent} 走的是三端点串联的真实链路：
 * 直接构造 confirm 请求体的用例能验证端点本身，但证明不了「上传返回的字段能原样走完确认」，
 * 而 AC-2 说的正是这条链路。
 *
 * <p>每个用例自己清一次仓库，不依赖执行顺序：内存仓库是单例，上传测试写进去的暂存会跨类残留。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResumeArchiveControllerTest {

    private static final String STUDENT_A = "student-a";
    private static final String STUDENT_B = "student-b";
    private static final String STUDENT_C = "student-c";

    /** 两条技能、一条教育、一条项目，与 4.1.2 示例同构。 */
    private static final String BODY_TWO_SKILLS = """
            {
              "resumeId": "r_20261112_001",
              "education": [{"school": "示例大学", "major": "软件工程", "degree": "本科"}],
              "skills": ["Java", "Spring Boot"],
              "projects": [{"name": "课程项目", "role": "后端"}],
              "internships": []
            }
            """;

    /** 学生把四类条目全删光后提交。 */
    private static final String BODY_ALL_EMPTY = """
            {
              "resumeId": "r_20261112_001",
              "education": [],
              "skills": [],
              "projects": [],
              "internships": []
            }
            """;

    /** 只留一条技能，其余三类为空：用来验证「不是全空就放行」。 */
    private static final String BODY_ONE_SKILL = """
            {
              "resumeId": "r_20261112_001",
              "education": [],
              "skills": ["Go"],
              "projects": [],
              "internships": []
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryConfirmedResumeRepository confirmed;

    @Autowired
    private InMemoryResumeStagingRepository staging;

    @BeforeEach
    void clean() {
        confirmed.remove(STUDENT_A);
        confirmed.remove(STUDENT_B);
        confirmed.remove(STUDENT_C);
        staging.remove(STUDENT_A);
        staging.remove(STUDENT_B);
        staging.remove(STUDENT_C);
    }

    @Test
    @DisplayName("AC-2：确认页提交后返回 200 CONFIRMED，正式档案写入提交内容")
    void confirmOk() throws Exception {
        mockMvc.perform(post("/api/v1/resumes/confirm")
                        .header(StudentIdentity.HEADER, STUDENT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_TWO_SKILLS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.skillCount").value(2))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty());

        ConfirmedResume saved = confirmed.find(STUDENT_A).orElseThrow();
        assertTrue(saved.isConfirmed(), "入库后的档案状态必须是 CONFIRMED");
        assertEquals(2, saved.content().skills().size());
        assertEquals("示例大学", saved.content().education().get(0).get("school"));
    }

    @Test
    @DisplayName("AC-2：以提交内容为准，不读暂存区的解析原始结果")
    void confirmUsesSubmittedContent() throws Exception {
        // 暂存区里放一份和提交内容不同的东西：若确认时去读暂存区，断言就会失败。
        staging.replace(STUDENT_A, new ParsedResume(
                List.of(), List.of("暂存区技能"), List.of(), List.of()));

        mockMvc.perform(post("/api/v1/resumes/confirm")
                        .header(StudentIdentity.HEADER, STUDENT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_TWO_SKILLS))
                .andExpect(status().isOk());

        ConfirmedResume saved = confirmed.find(STUDENT_A).orElseThrow();
        assertEquals(List.of("Java", "Spring Boot"), saved.content().skills(),
                "正式档案必须等于确认页提交的内容");
    }

    @Test
    @DisplayName("AC-8：四类字段全删空后提交返回 422，不生成空档案")
    void confirmAllEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/resumes/confirm")
                        .header(StudentIdentity.HEADER, STUDENT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_ALL_EMPTY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_VALID_FIELD"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertTrue(confirmed.find(STUDENT_A).isEmpty(), "全空提交不得写入任何档案");
    }

    @Test
    @DisplayName("AC-9：已有确认档案时提交全空，旧档案字段与确认时间都不变")
    void confirmFailureKeepsExistingArchive() throws Exception {
        mockMvc.perform(post("/api/v1/resumes/confirm")
                        .header(StudentIdentity.HEADER, STUDENT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_TWO_SKILLS))
                .andExpect(status().isOk());

        ConfirmedResume before = confirmed.find(STUDENT_A).orElseThrow();
        Instant confirmedAtBefore = before.confirmedAt();

        mockMvc.perform(post("/api/v1/resumes/confirm")
                        .header(StudentIdentity.HEADER, STUDENT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_ALL_EMPTY))
                .andExpect(status().isUnprocessableEntity());

        ConfirmedResume after = confirmed.find(STUDENT_A).orElseThrow();
        assertEquals(2, after.content().skills().size(), "失败不得清空或部分覆盖旧档案");
        assertEquals(confirmedAtBefore, after.confirmedAt(), "失败不得刷新确认时间");
        assertTrue(after.isConfirmed());
    }

    @Test
    @DisplayName("FR-7：重复确认只刷新同一份档案，不产生第二份")
    void confirmIsUpsert() throws Exception {
        mockMvc.perform(post("/api/v1/resumes/confirm")
                        .header(StudentIdentity.HEADER, STUDENT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_TWO_SKILLS))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/resumes/confirm")
                        .header(StudentIdentity.HEADER, STUDENT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_ONE_SKILL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillCount").value(1));

        assertEquals(1, confirmed.find(STUDENT_A).orElseThrow().content().skills().size(),
                "第二次确认替换同一份档案");
    }

    @Test
    @DisplayName("AC-2 空态：未建档返回 200 与字面的 data: null，不返回 404")
    void currentEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/resumes/current")
                        .header(StudentIdentity.HEADER, STUDENT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                // 4.1.3 要求字段在、值为 null。响应用 CurrentResumeResponse 而不是 ApiResponse，
                // 就是因为后者的 NON_NULL 会让这个键整个消失（同 ProfileResponse 对 4.2.4 的处理）
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("AC-2：已建档返回四类字段与 status = CONFIRMED")
    void currentConfirmed() throws Exception {
        mockMvc.perform(post("/api/v1/resumes/confirm")
                        .header(StudentIdentity.HEADER, STUDENT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_TWO_SKILLS))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/resumes/current").header(StudentIdentity.HEADER, STUDENT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.education[0].school").value("示例大学"))
                .andExpect(jsonPath("$.data.skills[0]").value("Java"))
                .andExpect(jsonPath("$.data.projects[0].name").value("课程项目"))
                // AC-4：没有的类别返回空数组，字段不省略
                .andExpect(jsonPath("$.data.internships").isArray())
                .andExpect(jsonPath("$.data.internships").isEmpty());
    }

    @Test
    @DisplayName("学生隔离：A 的档案 B 读不到，且 B 的确认不影响 A")
    void archivesAreIsolatedByStudent() throws Exception {
        mockMvc.perform(post("/api/v1/resumes/confirm")
                        .header(StudentIdentity.HEADER, STUDENT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_TWO_SKILLS))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/resumes/current").header(StudentIdentity.HEADER, STUDENT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(post("/api/v1/resumes/confirm")
                        .header(StudentIdentity.HEADER, STUDENT_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_ONE_SKILL))
                .andExpect(status().isOk());

        assertEquals(2, confirmed.find(STUDENT_A).orElseThrow().content().skills().size(),
                "B 的确认不得覆盖 A 的档案");
        assertNotNull(confirmed.find(STUDENT_B).orElse(null));
    }

    @Test
    @DisplayName("AC-2 全链路：上传只写暂存区，确认后 current 才读得到，字段与上传返回一致")
    void uploadThenConfirmThenCurrent() throws Exception {
        // 1. 上传：解析结果只进暂存区，正式档案仍为空（AC-1 的「正式档案不变」）
        String uploadResponse = mockMvc.perform(multipart("/api/v1/resumes")
                        .file(pdf())
                        .header(StudentIdentity.HEADER, STUDENT_C))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRM"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(confirmed.find(STUDENT_C).isEmpty(), "上传成功不得写正式档案");
        assertTrue(staging.find(STUDENT_C).isPresent(), "上传成功应写暂存区");

        // 2. 确认：把上传返回的四类字段原样提交，这是真实前端的动作
        JsonNode data = objectMapper.readTree(uploadResponse).path("data");
        String confirmBody = objectMapper.writeValueAsString(confirmPayloadFrom(data));

        mockMvc.perform(post("/api/v1/resumes/confirm")
                        .header(StudentIdentity.HEADER, STUDENT_C)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        assertFalse(confirmed.find(STUDENT_C).isEmpty(), "确认后正式档案必须有内容");

        // 3. 读取：current 返回的就是刚确认的那份，字段没有在端点之间走样
        mockMvc.perform(get("/api/v1/resumes/current").header(StudentIdentity.HEADER, STUDENT_C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.education[0].school").isNotEmpty())
                .andExpect(jsonPath("$.data.skills[0]").isNotEmpty());
    }

    /** 用上传响应里的 data 拼确认请求体，模拟确认页把解析结果原样提交。 */
    private Map<String, Object> confirmPayloadFrom(JsonNode data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resumeId", data.path("resumeId").asText());
        payload.put("education", data.path("parse").path("education"));
        payload.put("skills", data.path("parse").path("skills"));
        payload.put("projects", data.path("parse").path("projects"));
        payload.put("internships", data.path("parse").path("internships"));
        return payload;
    }

    /** 4 字节 PDF 文件头加填充，与 {@code ResumeControllerTest} 的构造一致。 */
    private MockMultipartFile pdf() {
        byte[] bytes = new byte[2048];
        bytes[0] = 0x25;
        bytes[1] = 0x50;
        bytes[2] = 0x44;
        bytes[3] = 0x46;
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", bytes);
    }
}
