package com.campus.career.mock;

import com.campus.career.common.ApiResponse;
import com.campus.career.common.RequestIdFilter;
import com.campus.career.common.StudentIdentity;
import com.campus.career.mock.dto.StartInterviewRequest;
import com.campus.career.mock.dto.StartInterviewResponse;
import com.campus.career.mock.dto.SubmitAnswerRequest;
import com.campus.career.mock.dto.SubmitAnswerResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M5 的 API 层，对应 docs/architecture.md 4.5.1 与 4.5.2。
 *
 * <p>本 PR 只实现端点 1、2（AC-1、AC-2、AC-4、AC-5）。端点 3
 * {@code POST /api/v1/mock-interviews/{sessionId}/report} 属 AC-3 的归档任务，
 * 归档幂等口径要与 M8 看板对齐后单独提交，不在本次范围。
 *
 * <p>学生身份由 {@link StudentIdentity} 统一解析（MVP1 无登录，见该类的说明）。
 */
@RestController
@RequestMapping("/api/v1/mock-interviews")
public class MockInterviewController {

    private final MockInterviewService service;

    public MockInterviewController(MockInterviewService service) {
        this.service = service;
    }

    /** 开始面试、抽第一道题（4.5.1、AC-1、AC-4）。 */
    @PostMapping
    public ResponseEntity<ApiResponse<StartInterviewResponse>> start(
            @RequestBody StartInterviewRequest payload,
            @RequestHeader(value = StudentIdentity.HEADER, required = false) String studentHeader,
            HttpServletRequest request) {
        StartInterviewResponse body = service.start(StudentIdentity.resolve(studentHeader),
                payload.jobId(), payload.jdText());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(requestId(request), body));
    }

    /** 提交单题作答并评分（4.5.2、AC-2、AC-5）。 */
    @PostMapping("/{sessionId}/answers")
    public ResponseEntity<ApiResponse<SubmitAnswerResponse>> answer(
            @PathVariable String sessionId,
            @Valid @RequestBody SubmitAnswerRequest payload,
            @RequestHeader(value = StudentIdentity.HEADER, required = false) String studentHeader,
            HttpServletRequest request) {
        SubmitAnswerResponse body = service.answer(StudentIdentity.resolve(studentHeader),
                sessionId, payload.questionId(), payload.answerText());
        return ResponseEntity.ok(ApiResponse.ok(requestId(request), body));
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }
}
