package com.campus.career.common;

import org.springframework.http.HttpStatus;

/**
 * M1 的业务错误码，逐个对应 docs/architecture.md 4.1.1 的失败响应表。
 *
 * <p>严格遵守「不新增 spec 与接口契约里没有的码」。新增错误码必须先改 spec，再回来改这里，
 * 不允许只在代码里加一个码。
 */
public enum ErrorCode {

    OK(HttpStatus.OK, "成功"),

    // ---- M1 上传与解析 ----
    /** 非 PDF / Word；或扩展名是 .pdf 但实际是图片、压缩包。AC-5、边界行 1、2 */
    RESUME_FORMAT_UNSUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "仅支持 PDF 与 Word 格式的简历"),

    /** Word 设了打开密码；或文件 0 字节、已损坏。AC-5、边界行 3、4 */
    RESUME_UNREADABLE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件无法读取，请确认未设置密码且未损坏"),

    /** 文件 > 10MB，恰好 10MB 放行。AC-3、边界行 5。提示文案取自 architecture 4.1.1 */
    RESUME_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "单份简历不超过 10MB"),

    /** 大模型 30 秒超时、重试 2 次后仍失败。AC-6、边界行 6。提示文案取自 spec AC-6 */
    PARSE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "解析超时，请稍后重试"),

    /** 模型输出非合法 JSON，或未过 JSON Schema 校验。AC-7、边界行 7 */
    MODEL_OUTPUT_INVALID(HttpStatus.BAD_GATEWAY, "解析结果格式异常，请稍后重试"),

    /** 解析流程完成但四类字段全空。AC-8、边界行 8 */
    NO_VALID_FIELD(HttpStatus.UNPROCESSABLE_ENTITY, "未能从简历中识别出有效内容，请更换文件重试"),

    // ---- M2 霍兰德问卷 ----
    /**
     * 30 题未全部作答就提交，或问卷未提交就请求生成画像。
     * AC-6、边界行 1 与 8。响应体需带未答题号，由 QuizIncompleteResponse 承载。
     */
    QUIZ_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "问卷未答完，请完成后提交"),

    /** 已成功提交后再次提交，或提交后再次修改作答。AC-7、边界行 2 */
    QUIZ_DUPLICATE_SUBMIT(HttpStatus.CONFLICT, "问卷已提交，请勿重复提交");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
