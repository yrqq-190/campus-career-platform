package com.campus.career.holland.persist;

import com.campus.career.holland.InMemoryQuizRepository;
import com.campus.career.holland.QuizRepository;
import org.junit.jupiter.api.DisplayName;

/**
 * 契约测试的内存实现子类。
 *
 * <p>只回答「怎么造一个干净实现」，全部断言语义继承自 {@link QuizRepositoryContractTest}。
 * 新增 JDBC 实现时照着这个类再写一个子类，用例一行都不用改。
 */
@DisplayName("QuizRepository 契约 · 内存实现")
class InMemoryQuizRepositoryContractTest extends QuizRepositoryContractTest {

    @Override
    protected QuizRepository newRepository() {
        return new InMemoryQuizRepository();
    }
}
