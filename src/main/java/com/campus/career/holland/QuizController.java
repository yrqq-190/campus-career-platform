package com.campus.career.holland;

import com.campus.career.common.ApiResponse;
import com.campus.career.common.RequestIdFilter;
import com.campus.career.holland.dto.QuestionsResponse;
import com.campus.career.holland.dto.SaveAnswerRequest;
import com.campus.career.holland.dto.SaveAnswerResponse;
import com.campus.career.holland.dto.SubmitResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M2 问卷的 API 层，对应 docs/architecture.md 4.2.1 ～ 4.2.3。
 *
 * <p>只做参数校验与路由，计分、完整性判定、重复提交判定全部在 {@link QuizService}。
 *
 * <h2>学生身份</h2>
 * 契约要求 Header 带 JWT，但 MVP1 不做账号注册登录（{@code spec/mvp1-scope.md} 第 31 行
 * 「账号注册登录 = 否，MVP2」）。为避免在鉴权上先行开发，本层先读 {@code X-Student-Id}
 * 请求头，缺省落到 {@link #DEFAULT_STUDENT_ID}。接入账号体系后由鉴权过滤器往同一位置注入
 * 真实学生标识，Controller 与 Service 都不用改。
 */
@RestController
@RequestMapping("/api/v1/holland")
public class QuizController {

    /** MVP1 占位标识。真实学生 identifier 要等账号体系（MVP2）。 */
    static final String DEFAULT_STUDENT_ID = "demo-student";

    private final QuizService service;

    public QuizController(QuizService service) {
        this.service = service;
    }

    /** 取 30 题与已保存作答（4.2.1、AC-4）。 */
    @GetMapping("/questions")
    public ResponseEntity<ApiResponse<QuestionsResponse>> questions(
            @RequestHeader(value = "X-Student-Id", required = false) String studentHeader,
            HttpServletRequest request) {
        QuestionsResponse body = service.questions(studentId(studentHeader));
        return ResponseEntity.ok(ApiResponse.ok(requestId(request), body));
    }

    /** 单题即时保存（4.2.2、FR-2）。 */
    @PutMapping("/answers")
    public ResponseEntity<ApiResponse<SaveAnswerResponse>> saveAnswer(
            @Valid @RequestBody SaveAnswerRequest payload,
            @RequestHeader(value = "X-Student-Id", required = false) String studentHeader,
            HttpServletRequest request) {
        SaveAnswerResponse body = service.saveAnswer(studentId(studentHeader),
                payload.questionId(), payload.option());
        return ResponseEntity.ok(ApiResponse.ok(requestId(request), body));
    }

    /** 提交问卷并计分（4.2.3、AC-1、AC-6、AC-7）。 */
    @PostMapping("/submissions")
    public ResponseEntity<ApiResponse<SubmitResponse>> submit(
            @RequestHeader(value = "X-Student-Id", required = false) String studentHeader,
            HttpServletRequest request) {
        SubmitResponse body = service.submit(studentId(studentHeader));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(requestId(request), body));
    }

    private String studentId(String header) {
        if (header == null || header.isBlank()) {
            return DEFAULT_STUDENT_ID;
        }
        return header.trim();
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }
}
