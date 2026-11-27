package com.campus.career.mock;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class MockInterviewAnswerAC2Test {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("AC-2：正常作答返回 200，score 在 0-100，hasNextQuestion 正确")
    void submitNormalAnswer() throws Exception {
        String sessionId = createSession();
        mockMvc.perform(post("/api/v1/mock-interviews/{sessionId}/answers", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":1,\"answerText\":\"我先看日志定位到订单服务超时，然后扩容并回滚配置，最后补了监控。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.score", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.score", lessThanOrEqualTo(100)))
                .andExpect(jsonPath("$.data.hasNextQuestion").value(true))
                .andExpect(jsonPath("$.data.nextQuestion.questionId").exists());
    }

    private String createSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jdText\":\"负责后端服务开发，熟悉 Java、Spring Boot、MySQL，参与高并发系统的设计与优化，要求三年以上经验，能独立排查线上问题。\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("sessionId").asText();
    }
}
