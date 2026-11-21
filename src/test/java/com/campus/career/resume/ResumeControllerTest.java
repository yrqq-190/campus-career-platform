package com.campus.career.resume;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.career.common.StudentIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 上传端点的契约测试，校验 HTTP 状态码与统一响应体是否与 architecture 4.1.1 一致。
 *
 * <p>PR 评审要的证据在这里：415 / 413 两类边界各有用例，成功路径验证四类字段恒存在、
 * 且结果只进暂存区（AC-1）。解析失败的三条分支（504 / 502 / 422）在
 * {@code ResumeParseFallbackControllerTest} 里验，两类用例分开是因为失败分支需要
 * 注入毫秒级的超时配置，与本类的默认上下文不是同一套。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResumeControllerTest {

    private static final long MB10 = 10L * 1024 * 1024;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryResumeStagingRepository staging;

    @Autowired
    private InMemoryConfirmedResumeRepository confirmed;

    @Test
    @DisplayName("AC-1：正常 PDF 返回 201，四类字段齐全，正式档案仍为空")
    void uploadOk() throws Exception {
        mockMvc.perform(multipart("/api/v1/resumes").file(pdf(2048)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRM"))
                .andExpect(jsonPath("$.data.resumeId").isNotEmpty())
                // AC-4：四类字段恒存在，没有的类别返回空数组而非省略
                .andExpect(jsonPath("$.data.parse.education").isArray())
                .andExpect(jsonPath("$.data.parse.skills").isArray())
                .andExpect(jsonPath("$.data.parse.projects").isArray())
                .andExpect(jsonPath("$.data.parse.internships").isArray())
                // 解析链路接入后不再是空数组，这里断言的是「字段结构对、内容进来了」
                .andExpect(jsonPath("$.data.parse.education[0].school").isNotEmpty())
                .andExpect(jsonPath("$.data.parse.skills[0]").isNotEmpty());

        // AC-1 的另一半：结果只进暂存区，正式档案仍为空
        String student = StudentIdentity.DEFAULT_STUDENT_ID;
        assertTrue(staging.find(student).isPresent(), "解析成功应写入暂存区");
        assertTrue(confirmed.find(student).isEmpty(), "未确认入库前正式档案必须为空");
    }

    @Test
    @DisplayName("AC-3：10MB + 1KB 返回 413 与 RESUME_TOO_LARGE")
    void tooLarge() throws Exception {
        mockMvc.perform(multipart("/api/v1/resumes").file(pdf(MB10 + 1024)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("RESUME_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("单份简历不超过 10MB"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("AC-5：上传 png 返回 415 与 RESUME_FORMAT_UNSUPPORTED")
    void unsupportedFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        mockMvc.perform(multipart("/api/v1/resumes").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("RESUME_FORMAT_UNSUPPORTED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("AC-5：加密 docx 返回 415 与 RESUME_UNREADABLE")
    void unreadable() throws Exception {
        byte[] ole2 = new byte[2048];
        ole2[0] = (byte) 0xD0;
        ole2[1] = (byte) 0xCF;
        ole2[2] = 0x11;
        ole2[3] = (byte) 0xE0;
        MockMultipartFile file = new MockMultipartFile(
                "file", "secret.docx", "application/octet-stream", ole2);
        mockMvc.perform(multipart("/api/v1/resumes").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("RESUME_UNREADABLE"));
    }

    @Test
    @DisplayName("缺少 file 字段时仍走统一响应体，不返回框架默认错误页")
    void missingPart() throws Exception {
        mockMvc.perform(multipart("/api/v1/resumes"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("RESUME_FORMAT_UNSUPPORTED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private MockMultipartFile pdf(long size) {
        byte[] bytes = new byte[(int) size];
        bytes[0] = 0x25;
        bytes[1] = 0x50;
        bytes[2] = 0x44;
        bytes[3] = 0x46;
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", bytes);
    }
}
