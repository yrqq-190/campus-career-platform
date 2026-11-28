package com.campus.career.holland.profile;

import com.campus.career.holland.HollandDimension;
import com.campus.career.holland.profile.dto.ProfileView;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * {@link ProfileRepository} 的内存实现，默认 profile 与测试用它。
 *
 * <p>整体替换用 {@code ConcurrentHashMap.put} 完成，这个操作对读者是原子的：
 * 要么读到旧画像，要么读到新画像，读不到「生成了一半」的中间态（NFR-3）。
 * 换数据库实现时，要保证同样的语义 —— 单条 UPSERT 加唯一键，别写成「先删后插」，
 * 那会在两步之间留下一段没有画像的窗口。
 *
 * <p>{@link #replace} 收到的 {@code scores} 在这里不保存：六维分数只写进数据库那张表的
 * {@code score_*} 列供排查用，读路径（{@link #find}）只返回画像四项，分数每次由
 * {@code ProfileService} 重新计算，不存在两份分数走岔的可能。
 */
@Repository
@Profile("!mysql")
public class InMemoryProfileRepository implements ProfileRepository {

    private final Map<String, ProfileView> store = new ConcurrentHashMap<>();

    @Override
    public Optional<ProfileView> find(String studentId) {
        return Optional.ofNullable(store.get(studentId));
    }

    @Override
    public void replace(String studentId, ProfileView profile, Map<HollandDimension, Integer> scores) {
        store.put(studentId, profile);
    }
}
