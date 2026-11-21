package com.campus.career.resume;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * {@link ConfirmedResumeRepository} 的内存实现，T1 建表前的占位。
 *
 * <p>测试用 {@link #replace} 直接铺一份「学生已确认过的档案 A」，
 * 这正是 AC-9 的 Given；生产环境在确认入库端点落地前不会有谁写它。
 */
@Repository
public class InMemoryConfirmedResumeRepository implements ConfirmedResumeRepository {

    private final Map<String, ConfirmedResume> confirmed = new ConcurrentHashMap<>();

    @Override
    public Optional<ConfirmedResume> find(String studentId) {
        return Optional.ofNullable(confirmed.get(studentId));
    }

    @Override
    public void replace(String studentId, ConfirmedResume resume) {
        confirmed.put(studentId, resume);
    }

    /** 清空某个学生的档案，测试用例之间隔离用。 */
    public void remove(String studentId) {
        confirmed.remove(studentId);
    }
}
