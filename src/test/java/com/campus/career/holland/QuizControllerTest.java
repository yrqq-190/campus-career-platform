package com.campus.career.holland;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 问卷端点的契约测试，校验 HTTP 状态码与响应体是否与 architecture 4.2.1 ～ 4.2.3 一致。
 *
 * <p>每个用例用各自的 {@code X-Student-Id} 隔离数据。仓储目前是内存单例，
 * 共用同一个学生标识会让用例之间互相污染（上一个用例答完的题会被下一个用例当成已答）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuizControllerTest {

    private static final String STUDENT_HEADER = "X-Student-Id";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("4.2.1：取题返回固定 30 道，每题四档选项，顺序从 1 开始")
    void questionsShape() throws Exception {
        mockMvc.perform(get("/api/v1/holland/questions").header(STUDENT_HEADER, "s-shape"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.questions", hasSize(30)))
                .andExpect(jsonPath("$.data.questions[0].id").value(1))
                .andExpect(jsonPath("$.data.questions[0].dimension").value("R"))
                .andExpect(jsonPath("$.data.questions[0].options", hasSize(4)))
                // 固定顺序：第 29 题是 E 维度的最后一题，第 30 题是 C 维度
                .andExpect(jsonPath("$.data.questions[28].id").value(29))
                .andExpect(jsonPath("$.data.questions[29].id").value(30))
                .andExpect(jsonPath("$.data.questions[29].dimension").value("C"))
                .andExpect(jsonPath("$.data.answeredCount").value(0))
                .andExpect(jsonPath("$.data.submitted").value(false));
    }

    @Test
    @DisplayName("4.2.2：单题保存返回 saved 与累加后的 answeredCount")
    void saveAnswer() throws Exception {
        mockMvc.perform(put("/api/v1/holland/answers")
                        .header(STUDENT_HEADER, "s-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":18,\"option\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.saved").value(true))
                .andExpect(jsonPath("$.data.answeredCount").value(1));
    }

    @Test
    @DisplayName("AC-4：答到第 17 题后重进，前 17 题原样恢复，第 18 题仍未作答")
    void resumeFromSeventeenth() throws Exception {
        String student = "s-resume";
        answerRange(student, 1, 17);

        mockMvc.perform(get("/api/v1/holland/questions").header(STUDENT_HEADER, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answeredCount").value(17))
                .andExpect(jsonPath("$.data.submitted").value(false))
                .andExpect(jsonPath("$.data.answers", aMapWithSize(17)))
                // 已答的题带着原选项回来
                .andExpect(jsonPath("$.data.answers['1']").value(3))
                .andExpect(jsonPath("$.data.answers['17']").value(3))
                // 未答的题不出现在 answers 里，前端渲染成未选中
                .andExpect(jsonPath("$.data.answers['18']").doesNotExist());
    }

    @Test
    @DisplayName("4.2.2 边界值：题号 30、选项 4 合法，不被 Bean Validation 拦下")
    void boundaryValuesAccepted() throws Exception {
        mockMvc.perform(put("/api/v1/holland/answers")
                        .header(STUDENT_HEADER, "s-bound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":30,\"option\":4}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("4.2.2：题号越界返回 400，且不新增业务错误码")
    void questionIdOutOfRange() throws Exception {
        mockMvc.perform(put("/api/v1/holland/answers")
                        .header(STUDENT_HEADER, "s-badq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":31,\"option\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("4.2.2：选项越界返回 400")
    void optionOutOfRange() throws Exception {
        mockMvc.perform(put("/api/v1/holland/answers")
                        .header(STUDENT_HEADER, "s-bado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":5,\"option\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("AC-6：答 10 题就提交，返回 422 并列出未答的 20 个题号")
    void submitIncomplete() throws Exception {
        String student = "s-incomplete";
        answerRange(student, 1, 10);

        mockMvc.perform(post("/api/v1/holland/submissions").header(STUDENT_HEADER, student))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUIZ_INCOMPLETE"))
                .andExpect(jsonPath("$.message").value("20 题未作答"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                // 契约要求 missing 在顶层，不是放在 data 里
                .andExpect(jsonPath("$.missing", hasSize(20)))
                .andExpect(jsonPath("$.missing[0]").value(11))
                .andExpect(jsonPath("$.missing[19]").value(30));

        // 边界行 1：拒绝时不生成画像数据，已答的 10 题仍然保留
        mockMvc.perform(get("/api/v1/holland/questions").header(STUDENT_HEADER, student))
                .andExpect(jsonPath("$.data.answeredCount").value(10))
                .andExpect(jsonPath("$.data.submitted").value(false));
    }

    @Test
    @DisplayName("AC-1：30 题答完提交，返回六维 0–100 分数且每维可追溯")
    void submitComplete() throws Exception {
        String student = "s-complete";
        answerRange(student, 1, 30);

        mockMvc.perform(post("/api/v1/holland/submissions").header(STUDENT_HEADER, student))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                // 全选「比较符合」时六维都是 67
                .andExpect(jsonPath("$.data.scores.R").value(67))
                .andExpect(jsonPath("$.data.scores.I").value(67))
                .andExpect(jsonPath("$.data.scores.A").value(67))
                .andExpect(jsonPath("$.data.scores.S").value(67))
                .andExpect(jsonPath("$.data.scores.E").value(67))
                .andExpect(jsonPath("$.data.scores.C").value(67));
    }

    @Test
    @DisplayName("AC-7：答完后重复提交，第二次返回 409 与 QUIZ_DUPLICATE_SUBMIT")
    void duplicateSubmit() throws Exception {
        String student = "s-dup";
        answerRange(student, 1, 30);

        mockMvc.perform(post("/api/v1/holland/submissions").header(STUDENT_HEADER, student))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/holland/submissions").header(STUDENT_HEADER, student))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUIZ_DUPLICATE_SUBMIT"));
    }

    @Test
    @DisplayName("4.2.2：提交后再修改作答返回 409，不覆盖已提交版本")
    void editAfterSubmitRejected() throws Exception {
        String student = "s-locked";
        answerRange(student, 1, 30);
        mockMvc.perform(post("/api/v1/holland/submissions").header(STUDENT_HEADER, student))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/holland/answers")
                        .header(STUDENT_HEADER, student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":1,\"option\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUIZ_DUPLICATE_SUBMIT"));
    }

    @Test
    @DisplayName("缺省学生标识时落到占位账号，接口不报错")
    void defaultStudentFallback() throws Exception {
        mockMvc.perform(get("/api/v1/holland/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions", hasSize(30)));
    }

    private void answerRange(String studentId, int from, int to) throws Exception {
        for (int i = from; i <= to; i++) {
            answer(studentId, i, 3);
        }
    }

    private void answer(String studentId, int questionId, int option) throws Exception {
        mockMvc.perform(put("/api/v1/holland/answers")
                        .header(STUDENT_HEADER, studentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":" + questionId + ",\"option\":" + option + "}"))
                .andExpect(status().isOk());
    }
}
