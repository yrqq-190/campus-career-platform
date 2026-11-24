package com.campus.career.common;

/**
 * MVP1 的学生身份占位。
 *
 * <p>接口契约要求 Header 带 JWT，但 {@code spec/mvp1-scope.md} 第 31 行写明
 * 「账号注册登录 = 否，MVP2」—— MVP1 没有登录，也就没有 token 可解析。
 *
 * <p>为了让各模块的数据隔离有落点、又不在鉴权上先行开发，统一在这里读一个请求头，
 * 缺省落到 {@link #DEFAULT_STUDENT_ID}。接入账号体系后，由鉴权过滤器把这个值换成
 * 真实学生标识，各 Controller 的调用位置不用改。
 *
 * <p>集中一处的另一个原因：如果每个 Controller 各写一份「读头、判空、给默认值」，
 * 迟早出现某个接口忘了 trim 或忘了给默认值，越权与串号的排查成本会很高。
 */
public final class StudentIdentity {

    /** 学生标识请求头。接入 JWT 后改为从 token subject 取。 */
    public static final String HEADER = "X-Student-Id";

    /** MVP1 占位学生标识。 */
    public static final String DEFAULT_STUDENT_ID = "demo-student";

    private StudentIdentity() {
    }

    /**
     * 解析学生标识。
     *
     * @param headerValue 请求头原始值，可为 null
     * @return 去掉首尾空白后的标识；缺省或空白时返回 {@link #DEFAULT_STUDENT_ID}
     */
    public static String resolve(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return DEFAULT_STUDENT_ID;
        }
        return headerValue.trim();
    }
}
