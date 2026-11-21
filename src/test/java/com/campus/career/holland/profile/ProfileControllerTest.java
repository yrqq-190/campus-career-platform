package com.campus.career.holland.profile;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 画像端点的契约测试，对应 architecture 4.2.3 的 profile 部分、4.2.4、4.2.5。
 *
 * <p>档案与意向都通过内存占位实现注入（T1 未合并，没有真实档案可读），
 * 每个用例用各自的学生标识隔离数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileControllerTest {

    private static final String H = "X-Student-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryResumeProfileSource resumeSource;

    @Autowired
    private InMemoryStudentIntentRepository intentRepository;

    @Test
    @DisplayName("4.2.4：尚未生成画像时返回 data 为 null，空态不报错")
    void profileAbsentReturnsNull() throws Exception {
        mockMvc.perform(get("/api/v1/holland/profile").header(H, "p-none"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                // 契约要求字段存在且为 null，不是把字段省掉
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("4.2.3：提交问卷后响应里直接带出画像四项")
    void submitReturnsProfile() throws Exception {
        String student = "p-submit";
        setUpResume(student, List.of("Java", "SQL", "Git", "沟通表达", "Excel", "英语"),
                List.of("杭州", "苏州"), List.of("互联网"));
        answerAll(student);

        mockMvc.perform(post("/api/v1/holland/submissions").header(H, student))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scores.R").exists())
                .andExpect(jsonPath("$.data.profile.directions", hasSize(4)))
                .andExpect(jsonPath("$.data.profile.directions[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data.profile.directions[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.data.profile.cities[0]").value("杭州"))
                .andExpect(jsonPath("$.data.profile.industries[0]").value("互联网"));
    }

    @Test
    @DisplayName("4.2.4：提交后能读回同一份画像")
    void profileReadableAfterSubmit() throws Exception {
        String student = "p-read";
        setUpResume(student, List.of("Python", "SQL", "统计分析"), List.of(), List.of());
        answerAll(student);
        mockMvc.perform(post("/api/v1/holland/submissions").header(H, student))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/holland/profile").header(H, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.directions", hasSize(4)))
                .andExpect(jsonPath("$.data.cities").isArray());
    }

    @Test
    @DisplayName("AC-5：档案技能为空时 skillGaps 是对象标记，不是空数组、也不报错")
    void skillGapsIsMarkerWhenSkillsEmpty() throws Exception {
        String student = "p-noskill";
        // 只填意向，不写档案，模拟「简历从未确认」
        intentRepository.save(student, new StudentIntent(List.of("武汉"), List.of("教育")));
        answerAll(student);

        mockMvc.perform(post("/api/v1/holland/submissions").header(H, student))
                .andExpect(status().isCreated())
                // 是对象而不是数组：断言它的 status 字段
                .andExpect(jsonPath("$.data.profile.skillGaps.status").value("PROFILE_SKILL_MISSING"))
                // 兴趣、城市、行业照常返回
                .andExpect(jsonPath("$.data.profile.directions", hasSize(4)))
                .andExpect(jsonPath("$.data.profile.cities[0]").value("武汉"))
                .andExpect(jsonPath("$.data.profile.industries[0]").value("教育"));
    }

    @Test
    @DisplayName("AC-3：技能更新后重新生成，读到的是新画像，旧内容不再存在")
    void regenerateReplacesProfile() throws Exception {
        String student = "p-regen";
        setUpResume(student, List.of("Java"), List.of(), List.of());
        answerAll(student);

        // 只有 Java 时，后端开发方向的理由里只提 Java
        mockMvc.perform(post("/api/v1/holland/submissions").header(H, student))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.profile.directions[0].reason")
                        .value(containsString("已具备 Java")))
                .andExpect(jsonPath("$.data.profile.directions[0].reason")
                        .value(org.hamcrest.Matchers.not(containsString("Redis"))));

        // 档案更新为 6 项技能后重新生成
        resumeSource.put(student, new ResumeSnapshot(
                List.of("Java", "SQL", "Spring Boot", "Redis", "Python", "统计分析"),
                List.of(), List.of(), List.of()));

        mockMvc.perform(post("/api/v1/holland/profile/regenerate").header(H, student))
                .andExpect(status().isOk())
                // 4.2.5 的 data 就是画像本身，不像 4.2.3 那样嵌在 profile 字段里
                .andExpect(jsonPath("$.data.directions", hasSize(4)))
                .andExpect(jsonPath("$.data.directions[0].reason")
                        .value(containsString("Redis")));

        // 再读一次，读到的必须是新画像；若旧画像还在，这里会拿到不含 Redis 的那份
        mockMvc.perform(get("/api/v1/holland/profile").header(H, student))
                .andExpect(jsonPath("$.data.directions[0].reason").value(containsString("Redis")));
    }

    @Test
    @DisplayName("4.2.5：问卷未提交就请求重新生成，返回 422 与 QUIZ_INCOMPLETE")
    void regenerateBeforeSubmit() throws Exception {
        mockMvc.perform(post("/api/v1/holland/profile/regenerate").header(H, "p-unsubmitted"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUIZ_INCOMPLETE"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("验收条件第 9 条：意向未填时 cities 与 industries 是空数组")
    void emptyIntentFields() throws Exception {
        String student = "p-nointent";
        setUpResume(student, List.of("Figma", "交互设计"), List.of(), List.of());
        answerAll(student);

        mockMvc.perform(post("/api/v1/holland/submissions").header(H, student))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.profile.cities", hasSize(0)))
                .andExpect(jsonPath("$.data.profile.industries", hasSize(0)));
    }

    @Test
    @DisplayName("缺省学生标识时读画像不报错，落到占位账号")
    void defaultStudentFallback() throws Exception {
        mockMvc.perform(get("/api/v1/holland/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    private void setUpResume(String studentId, List<String> skills,
                             List<String> cities, List<String> industries) {
        resumeSource.put(studentId, new ResumeSnapshot(skills, List.of(), List.of(), List.of()));
        intentRepository.save(studentId, new StudentIntent(cities, industries));
    }

    private void answerAll(String studentId) throws Exception {
        for (int i = 1; i <= 30; i++) {
            mockMvc.perform(put("/api/v1/holland/answers")
                            .header(H, studentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"questionId\":" + i + ",\"option\":3}"))
                    .andExpect(status().isOk());
        }
    }
}
