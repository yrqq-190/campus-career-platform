package com.campus.career.mock;

import org.springframework.http.HttpStatus;

/**
 * M5 的失败信号，携带「状态码 + 响应体 code」两项。
 *
 * <p>为什么不复用 {@code common.BusinessException} + {@code common.ErrorCode}：
 * M5 的失败响应里只有 {@code JD_REQUIRED} 是 spec 定义过的业务码，404 / 409 在
 * architecture 4.5 的「错误码补充说明」里写明<b>无专属业务码</b>（属于通用状态机约束）。
 * 往 {@code ErrorCode} 里塞 NOT_FOUND / CONFLICT 会污染 M1/M2 的错误码白名单，
 * 所以 M5 自己带一份，形态与 {@code QuizIncompleteException} 同思路。
 *
 * <p>{@code NOT_FOUND} / {@code CONFLICT} 是框架兜底码，仅用于让失败响应体保持
 * {@code {"code":"...","message":"...","requestId":"..."}} 的同一形态，不属于业务错误码。
 */
public class MockInterviewException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String code;

    private MockInterviewException(HttpStatus httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    /**
     * JD 缺失或过短（AC-4、边界表「JD 为空」「粘贴文本过短」）。
     *
     * <p>文案取自 spec 边界表：提示「请先输入岗位或粘贴 JD」并阻断流程。
     */
    public static MockInterviewException jdRequired() {
        return new MockInterviewException(HttpStatus.BAD_REQUEST, "JD_REQUIRED",
                "请先输入岗位或粘贴 JD");
    }

    /** 会话不存在，或不属于当前学生——两种情况合并成同一个响应，不暴露会话存在性（4.5.2）。 */
    public static MockInterviewException sessionNotFound() {
        return new MockInterviewException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "面试会话不存在或无权访问");
    }

    /** 会话已归档或已取消，仍提交作答（4.5.2 失败表第二行）。 */
    public static MockInterviewException sessionNotAnswerable() {
        return new MockInterviewException(HttpStatus.CONFLICT, "CONFLICT",
                "当前会话不接受作答，请重新开始面试");
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }
}
