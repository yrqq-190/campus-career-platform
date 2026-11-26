package com.campus.career.holland.persist;

import com.campus.career.holland.profile.InMemoryProfileRepository;
import com.campus.career.holland.profile.ProfileRepository;
import org.junit.jupiter.api.DisplayName;

/**
 * 契约测试的内存实现子类。
 *
 * <p>只回答「怎么造一个干净实现」，全部断言语义继承自 {@link ProfileRepositoryContractTest}。
 */
@DisplayName("ProfileRepository 契约 · 内存实现")
class InMemoryProfileRepositoryContractTest extends ProfileRepositoryContractTest {

    @Override
    protected ProfileRepository newRepository() {
        return new InMemoryProfileRepository();
    }
}
