package com.campus.career.resume;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** {@link ResumeStagingRepository} 的内存实现，T1 建表前的占位。 */
@Repository
public class InMemoryResumeStagingRepository implements ResumeStagingRepository {

    private final Map<String, ParsedResume> staged = new ConcurrentHashMap<>();

    @Override
    public Optional<ParsedResume> find(String studentId) {
        return Optional.ofNullable(staged.get(studentId));
    }

    @Override
    public void replace(String studentId, ParsedResume parsed) {
        staged.put(studentId, parsed);
    }

    /** 清空某个学生的暂存，测试用例之间隔离用。 */
    public void remove(String studentId) {
        staged.remove(studentId);
    }
}
