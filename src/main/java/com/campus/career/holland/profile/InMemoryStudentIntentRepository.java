package com.campus.career.holland.profile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * {@link StudentIntentRepository} 的内存实现，T1 合并前的占位。
 *
 * <p>与档案快照同理：只保证画像侧能读到意向，不承担「学生怎么填进来」这件事，
 * 那需要一个契约里还不存在的写入端点。
 */
@Repository
public class InMemoryStudentIntentRepository implements StudentIntentRepository {

    private final Map<String, StudentIntent> store = new ConcurrentHashMap<>();

    @Override
    public StudentIntent find(String studentId) {
        StudentIntent intent = store.get(studentId);
        return intent == null ? StudentIntent.empty() : intent;
    }

    @Override
    public void save(String studentId, StudentIntent intent) {
        store.put(studentId, intent);
    }
}
