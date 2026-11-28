package com.campus.career.common;

import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@link StudentDirectory} 的数据库实现，按学号查主键。
 *
 * <p>只在 mysql profile 下装配：默认 profile 用内存仓储，不需要 {@code student.id}。
 */
@Component
@Profile("mysql")
public class MybatisStudentDirectory implements StudentDirectory {

    private final StudentMapper studentMapper;

    public MybatisStudentDirectory(StudentMapper studentMapper) {
        this.studentMapper = studentMapper;
    }

    @Override
    public Optional<Long> findInternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(studentMapper.findIdByStudentNo(externalId.trim()));
    }
}
