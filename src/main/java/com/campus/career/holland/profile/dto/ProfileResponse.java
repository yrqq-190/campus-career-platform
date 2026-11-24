package com.campus.career.holland.profile.dto;

/**
 * 读画像与重新生成的响应外壳，对应 architecture 4.2.4：
 *
 * <pre>
 * { "code":"OK", "requestId":"...", "data": {...} }   有画像
 * { "code":"OK", "requestId":"...", "data": null }    尚未生成
 * </pre>
 *
 * <p>为什么不复用 {@code common.ApiResponse}：那个类标了 {@code @JsonInclude(NON_NULL)}，
 * {@code data} 为 null 时整个字段会被省略，而 4.2.4 明确要求「尚未生成画像时返回 {@code "data": null}，
 * 空态不报错」—— 字段消失和字段为 null 对前端是两种判断分支，不能混。
 * 这里不加 NON_NULL，让 data 老老实实输出 null。
 */
public record ProfileResponse(String code, String requestId, ProfileView data) {

    public static ProfileResponse ok(String requestId, ProfileView data) {
        return new ProfileResponse("OK", requestId, data);
    }
}
