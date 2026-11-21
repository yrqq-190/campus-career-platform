package com.campus.career.resume;

import java.util.Optional;

/**
 * 正式档案的读写端口。
 *
 * <p>「同一学生任意时刻只有一份有效档案」（FR-1），所以只有 {@link #replace}，
 * 没有新增与版本方法：FR-7 明确不保存、不展示、不比对历史版本。
 *
 * <p>本周没有写入正式档案的入口——{@code POST /api/v1/resumes/confirm} 属 architecture 4.1.2，
 * 等 T1 建表后接。本接口现在只承担两件事：
 * <ol>
 *   <li>给 AC-9 一个可断言的「档案 A」落点：解析失败后 {@link #find} 读到的必须还是原来那一份；
 *   <li>给画像侧 {@code ResumeProfileSource} 一个能读的真实来源，见
 *       {@code holland.profile.ArchiveResumeProfileSource}。
 * </ol>
 *
 * <p>刻意不提供「清空」方法：FR-8 要求失败时任一步都不得清空档案，
 * 接口上没有这个动作，就不会有人写错。
 */
public interface ConfirmedResumeRepository {

    /** 学生当前已确认的档案；从未确认过时返回空，空态不是错误。 */
    Optional<ConfirmedResume> find(String studentId);

    /** 整份替换该学生的正式档案（FR-7：整体替换，不留历史版本）。 */
    void replace(String studentId, ConfirmedResume resume);
}
