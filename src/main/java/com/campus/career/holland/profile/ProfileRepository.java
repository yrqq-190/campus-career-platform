package com.campus.career.holland.profile;

import com.campus.career.holland.HollandDimension;
import com.campus.career.holland.profile.dto.ProfileView;
import java.util.Map;
import java.util.Optional;

/**
 * 画像存储端口。
 *
 * <p>接口只有一个写入方法 {@link #replace}，没有 insert / update 之分 —— 这是 AC-3 与 NFR-3 的
 * 结构保证：同一学生只有一份有效画像，新生成立刻整体替换旧的，不存在「两份并存」或
 * 「改到一半」的状态。用 upsert 语义的接口，从类型上就不给人写出并存代码的机会。
 */
public interface ProfileRepository {

    Optional<ProfileView> find(String studentId);

    /**
     * 整体替换该学生的画像；原先那份不再可读（AC-3、FR-7）。
     *
     * <p>额外要求传 {@code scores}，是因为 {@code holland_profile} 把六维分数与 {@code top_dimension}
     * 与画像存在同一行（{@code db/schema.sql} 第 76–96 行，其中 {@code top_dimension} 是 NOT NULL
     * 且无默认值）。画像四项本身不含分数，只传 {@link ProfileView} 时 {@code top_dimension} 无从取值。
     *
     * <p>内存实现用不到它，但契约要统一 —— 否则 JDBC 实现只能自己再去算一遍，两份计分逻辑迟早走岔。
     */
    void replace(String studentId, ProfileView profile, Map<HollandDimension, Integer> scores);
}
