package com.campus.career.holland;

import com.campus.career.common.ApiResponse;
import com.campus.career.common.RequestIdFilter;
import com.campus.career.common.StudentIdentity;
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
 * <p>学生身份由 {@link StudentIdentity} 统一解析，说明见那个类。
 */
@RestController
@RequestMapping("/api/v1/holland")
public class QuizController {

    private final QuizService service;

    public QuizController(QuizService service) {
        this.service = service;
    }

    /** 取 30 题与已保存作答（4.2.1、AC-4）。 */
    @GetMapping("/questions")
    public ResponseEntity<ApiResponse<QuestionsResponse>> questions(
            @RequestHeader(value = StudentIdentity.HEADER, required = false) String studentHeader,
            HttpServletRequest request) {
        QuestionsResponse body = service.questions(StudentIdentity.resolve(studentHeader));
        return ResponseEntity.ok(ApiResponse.ok(requestId(request), body));
    }

    /** 单题即时保存（4.2.2、FR-2）。 */
    @PutMapping("/answers")
    public ResponseEntity<ApiResponse<SaveAnswerResponse>> saveAnswer(
            @Valid @RequestBody SaveAnswerRequest payload,
            @RequestHeader(value = StudentIdentity.HEADER, required = false) String studentHeader,
            HttpServletRequest request) {
        SaveAnswerResponse body = service.saveAnswer(StudentIdentity.resolve(studentHeader),
                payload.questionId(), payload.option());
        return ResponseEntity.ok(ApiResponse.ok(requestId(request), body));
    }

    /** 提交问卷、计分并返回画像（4.2.3、AC-1、AC-6、AC-7）。 */
    @PostMapping("/submissions")
    public ResponseEntity<ApiResponse<SubmitResponse>> submit(
            @RequestHeader(value = StudentIdentity.HEADER, required = false) String studentHeader,
            HttpServletRequest request) {
        SubmitResponse body = service.submit(StudentIdentity.resolve(studentHeader));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(requestId(request), body));
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }
}
