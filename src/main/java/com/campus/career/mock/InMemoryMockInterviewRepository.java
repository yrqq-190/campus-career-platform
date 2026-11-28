package com.campus.career.mock;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

/**
 * 内存实现，仅用于本周（#123 表已建、落库实现属后续任务）。
 *
 * <p>与应用同生命周期，不做过期清理：MVP1 是演示环境，会话量级是个位数；
 * 落库实现接上后本类直接删掉即可。写在这里而不是标注 {@code @Profile}，
 * 是因为落库实现还没写，加了条件反而让本地跑不起来。
 */
@Repository
public class InMemoryMockInterviewRepository implements MockInterviewRepository {

    private final ConcurrentMap<String, MockInterviewSession> sessions = new ConcurrentHashMap<>();

    @Override
    public MockInterviewSession create(MockInterviewSession session) {
        sessions.put(session.sessionId(), session);
        return session;
    }

    @Override
    public Optional<MockInterviewSession> find(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public void save(MockInterviewSession session) {
        sessions.put(session.sessionId(), session);
    }
}
