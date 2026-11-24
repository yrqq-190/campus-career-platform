package com.campus.career.holland.profile;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * {@link ResumeProfileSource} 的内存实现，T1 合并前的占位。
 *
 * <p>数据靠 {@link #put} 写入：测试用它构造 AC-2（6 项技能）与 AC-5（技能为空）两种场景。
 * 生产环境不会有谁调用这个方法，等 M1 的档案接口落地后整个类被适配器替换。
 *
 * <p>这里没有模拟「未确认」的中间态：{@code put} 进去的就是已确认的结果，
 * 因为「未确认不得作为画像输入」这条约束在 M1 侧就该守住，画像侧不重复建模。
 */
@Component
public class InMemoryResumeProfileSource implements ResumeProfileSource {

    private final Map<String, ResumeSnapshot> confirmed = new ConcurrentHashMap<>();

    @Override
    public Optional<ResumeSnapshot> findConfirmed(String studentId) {
        return Optional.ofNullable(confirmed.get(studentId));
    }

    /** 写入一份已确认档案，占位用。 */
    public void put(String studentId, ResumeSnapshot snapshot) {
        confirmed.put(studentId, snapshot);
    }

    /** 清空某个学生的档案，测试用。 */
    public void remove(String studentId) {
        confirmed.remove(studentId);
    }
}
