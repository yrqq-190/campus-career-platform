package com.campus.career.common;

import java.util.Optional;

/**
 * 外部学生标识到数据库主键的转换。
 *
 * <p>为什么需要这一层：请求头 {@code X-Student-Id} 里放的是字符串（MVP1 没有登录，
 * 见 {@link StudentIdentity}），而 {@code db/schema.sql} 里 5 张业务表的 {@code student_id}
 * 都是 {@code BIGINT NOT NULL} 且外键指向 {@code student(id)}。字符串既写不进 BIGINT，
 * 也没有对应的主键值，落库会分别撞上 1366 与 1452。
 *
 * <p>约定：请求头的值就是学号，对应 {@code student.student_no}。这一层只做「查」，
 * 不负责建档 —— MVP1 没有注册流程（{@code spec/mvp1-scope.md} 第 31 行），
 * 学生行由 {@code db/seed.sql} 准备。将来接入账号体系，把实现换成「查 JWT subject 对应的行」即可，
 * 各 Service 收的仍然是字符串，一处不用改。
 *
 * <p>只在 mysql profile 下有实现：默认 profile 走内存仓储，全程不接触 {@code student.id}。
 */
public interface StudentDirectory {

    /**
     * 按外部标识查内部主键。
     *
     * @param externalId 请求头里的学生标识，按学号解释
     * @return 空表示 {@code student} 表里没有这个学号；调用方应据此返回 404 或 422，
     *         不要静默兜底到某个默认学生，那会把不同学生的数据串到一起
     */
    Optional<Long> findInternalId(String externalId);
}
