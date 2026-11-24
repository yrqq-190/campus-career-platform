package com.campus.career.resume;

/**
 * {@code GET /api/v1/resumes/current} 的响应外壳，对应 docs/architecture.md 4.1.3：
 *
 * <pre>
 * { "code":"OK", "requestId":"...", "data": {...} }   已建档
 * { "code":"OK", "requestId":"...", "data": null }    未建档
 * </pre>
 *
 * <p><b>为什么不复用 {@code common.ApiResponse}</b>：那个类标了 {@code @JsonInclude(NON_NULL)}，
 * {@code data} 为 null 时整个字段会被省略，而 4.1.3 明确要求「未建档返回 {@code "data": null}，
 * 空态不是错误」—— 字段消失与字段为 null 对前端是两种判断分支，不能混。
 *
 * <p>这个取舍不是本 PR 新立的：{@code holland.profile.dto.ProfileResponse} 在 4.2.4 遇到同一情形
 * （「尚未生成画像时返回 {@code "data": null}」）就是这么处理的——另造一个不带 NON_NULL 的壳，
 * 而不是去改公共的 {@code ApiResponse}（那样会让失败响应也多出 {@code "data": null}，
 * 与 4.1.1「失败不带 data」相反）。M1 照同一口径办，两个模块的空态在外观上保持一致。
 *
 * <p>只带 {@code code} / {@code requestId} / {@code data} 三个字段，与 {@code ProfileResponse} 同构；
 * 成功响应不带 {@code message}，与 {@code ApiResponse} 的成功形态也一致。
 *
 * @param code      固定 {@code OK}
 * @param requestId 请求标识，供排查时串起一次调用（NFR-3）
 * @param data      已确认档案；未建档时为 {@code null}
 */
public record CurrentResumeResponse(String code, String requestId, ResumeView data) {

    public static CurrentResumeResponse ok(String requestId, ResumeView data) {
        return new CurrentResumeResponse("OK", requestId, data);
    }
}
