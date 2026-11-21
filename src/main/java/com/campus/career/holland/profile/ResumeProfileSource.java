package com.campus.career.holland.profile;

import java.util.Optional;

/**
 * 画像读取 M1 已确认档案的端口。
 *
 * <p><b>这是一个待接线的插座。</b>T1（#107）的建表 PR 尚未合并，M1 也没有档案读写实现
 * （T2 的 {@code upload} 只返回暂存结构，不落正式档案），所以本周画像拿不到真实档案。
 * 按「依赖未完成只做契约骨架 + Mock」的约束，这里先定接口，配一个内存实现；
 * M1 的 {@code GET /resumes/current} 落地后，写一个实现本接口的适配器即可，
 * 画像合成逻辑一行不用改。
 *
 * <p>返回 {@link Optional} 而不是 null 或抛异常：「学生还没确认过简历」是正常业务状态，
 * 对应 AC-5 的降级路径，不是错误。
 */
public interface ResumeProfileSource {

    /** 学生当前已确认的档案；从未确认过时返回 {@link Optional#empty()}。 */
    Optional<ResumeSnapshot> findConfirmed(String studentId);
}
