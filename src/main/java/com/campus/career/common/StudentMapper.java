package com.campus.career.common;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * {@code student} 表的最小读取接口，只用来把学号换成主键。
 *
 * <p>不建实体类、不用 MyBatis-Plus 的 {@code BaseMapper}：这一处只需要一列，
 * 拉一整套实体映射反而多一层要维护的东西。也正因为不依赖实体，
 * 这张表将来加字段不影响这里。
 *
 * <p>只在 mysql profile 下被注册：默认 profile 排除了 MyBatis-Plus 的自动配置，
 * {@code @Mapper} 扫描不会发生，所以本接口在本机无库时没有任何 bean 引用它。
 */
@Mapper
public interface StudentMapper {

    @Select("SELECT id FROM student WHERE student_no = #{studentNo}")
    Long findIdByStudentNo(@Param("studentNo") String studentNo);
}
