package com.campus.career.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 统一响应体，字段与 docs/architecture.md 4.1.1 完全一致。
 *
 * <p>成功：{@code {"code":"OK","requestId":"...","data":{...}}}
 * <p>失败：{@code {"code":"RESUME_TOO_LARGE","message":"单份简历不超过 10MB","requestId":"..."}}
 *
 * <p>成功响应不带 message、失败响应不带 data，靠 {@link JsonInclude} 让空字段不出现，
 * 不为两种形态各造一个类。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "message", "requestId", "data"})
public record ApiResponse<T>(String code, String message, String requestId, T data) {

    public static <T> ApiResponse<T> ok(String requestId, T data) {
        return new ApiResponse<>(ErrorCode.OK.name(), null, requestId, data);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message, String requestId) {
        return new ApiResponse<>(errorCode.name(), message, requestId, null);
    }
}
