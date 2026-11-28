package com.campus.career.mock;

import java.util.Optional;

/**
 * M5 会话与作答的存储契约。
 *
 * <p>抽出接口的理由与 M2 的 {@code QuizRepository} 一致：#123 的建表 PR 已合、落库实现还没写，
 * 本周先用内存实现把契约与业务逻辑跑通；补 {@code JdbcMockInterviewRepository} 实现本接口后，
 * Service 与 Controller 一行都不用改。
 *
 * <p>接口只有三个方法：会话是不可变主体 + 可变的作答集合，读出来直接改对象、再 {@code save} 回去，
 * 比拆成「查会话 / 写作答 / 改状态」三个仓储方法更容易保证一次请求内的原子性。
 */
public interface MockInterviewRepository {

    /** 新建会话。 */
    MockInterviewSession create(MockInterviewSession session);

    /** 按会话 ID 查；不存在返回空，由 Service 映射成 404（不区分「不存在」与「不属于你」）。 */
    Optional<MockInterviewSession> find(String sessionId);

    /** 回写会话状态与作答。内存实现下对象本就是同一个引用，保留此方法是为了落库后语义不变。 */
    void save(MockInterviewSession session);
}
