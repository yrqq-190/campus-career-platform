package com.campus.career.job;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.career.holland.profile.InMemoryResumeProfileSource;
import com.campus.career.holland.profile.ResumeSnapshot;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * M3 岗位推荐的契约测试，只覆盖 module-03 AC-1 与 AC-3。
 *
 * <p>AC-1：画像中的技能与岗位要求命中时，推荐理由必须写明命中的技能。
 * 文案格式取 architecture 4.3 的示例「命中技能：Vue、JavaScript」，多技能用顿号连接。
 *
 * <p>AC-3：外链失效必须降级，而不是整个列表失败：
 * <ul>
 *   <li>推荐列表仍返回 200，失效岗位卡片带 {@code linkStatus=INVALID} 与重检关键词；</li>
 *   <li>点击失效外链返回 404 {@code JOB_LINK_INVALID}，并给出同关键词重检链接；</li>
 *   <li>可达外链正常返回 200，不被降级逻辑误伤。</li>
 * </ul>
 *
 * <p>本类假设 M3 的演示岗位目录固定包含：
 * <ul>
 *   <li>{@code J001}：要求 Java、SQL、Spring Boot，外链可达；</li>
 *   <li>{@code J003}：要求 Python，外链已失效。</li>
 * </ul>
 * 技能通过 M2 的 {@link InMemoryResumeProfileSource} 注入，每个用例用各自的学生标识隔离数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
class JobRecommendationControllerTest {

    private static final String H = "X-Student-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryResumeProfileSource resumeSource;

    @Test
    @DisplayName("AC-1：画像含 Java 且岗位要求 Java 时，推荐理由展示「命中技能：Java」")
    void reasonShowsHitSkill() throws Exception {
        String student = "m3-ac1-java";
        resumeSource.put(student, new ResumeSnapshot(List.of("Java"), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/jobs/recommendations").header(H, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                // 按 jobId 取卡片，不依赖列表排序
                .andExpect(jsonPath("$.data[?(@.jobId == 'J001')].reason", hasItem("命中技能：Java")))
                .andExpect(jsonPath("$.data[?(@.jobId == 'J001')].sourceUrl",
                        hasItem(startsWith("https://"))));
    }

    @Test
    @DisplayName("AC-1：命中多项技能时，理由按岗位要求顺序列全，不混入未命中项")
    void reasonListsAllHitSkills() throws Exception {
        String student = "m3-ac1-multi";
        resumeSource.put(student, new ResumeSnapshot(
                List.of("Java", "SQL", "Spring Boot", "Python"),
                List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/jobs/recommendations").header(H, student))
                .andExpect(status().isOk())
                // Python 不是 J001 的要求，不能出现在理由里
                .andExpect(jsonPath("$.data[?(@.jobId == 'J001')].reason",
                        hasItem("命中技能：Java、SQL、Spring Boot")));
    }

    @Test
    @DisplayName("AC-3：单条外链失效时列表仍返回 200，卡片标记 INVALID 并带重检关键词")
    void invalidLinkDegradesOnCard() throws Exception {
        String student = "m3-ac3-list";
        resumeSource.put(student, new ResumeSnapshot(List.of("Python"), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/jobs/recommendations").header(H, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                // 失效来源仍要能展示，所以 sourceUrl 照常返回
                .andExpect(jsonPath("$.data[?(@.jobId == 'J003')].sourceUrl",
                        hasItem(startsWith("https://"))))
                // 卡片标「失效来源」
                .andExpect(jsonPath("$.data[?(@.jobId == 'J003')].linkStatus", hasItem("INVALID")))
                // 「按关键词重新检索」所需的关键词
                .andExpect(jsonPath("$.data[?(@.jobId == 'J003')].retryKeyword",
                        hasItem(not(emptyString()))));
    }

    @Test
    @DisplayName("AC-3：点击失效外链返回 404 JOB_LINK_INVALID，并给同关键词重检链接")
    void clickInvalidLinkReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/J003/link").header(H, "m3-ac3-click"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_LINK_INVALID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.message").value("该链接可能已失效，已为您按关键词重新检索"))
                .andExpect(jsonPath("$.retryKeyword").value(not(emptyString())))
                .andExpect(jsonPath("$.retryUrl").value(startsWith("https://")));
    }

    @Test
    @DisplayName("AC-3：可达外链正常返回 200，不被失效降级逻辑误伤")
    void clickValidLinkReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/J001/link").header(H, "m3-ac3-valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.jobId").value("J001"))
                .andExpect(jsonPath("$.data.sourceUrl").value(startsWith("https://")));
    }
}
