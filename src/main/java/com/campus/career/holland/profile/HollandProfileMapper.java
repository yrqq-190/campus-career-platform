package com.campus.career.holland.profile;

import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * {@code holland_profile} 的读写，只服务画像落库。
 *
 * <p>参数与返回值都用 {@code Map} 而不是实体类：这张表有 15 列，其中 4 列是 JSON、
 * 1 列是 NOT NULL 的 {@code top_dimension}，用实体类就要额外维护一套字段映射与构造顺序，
 * 而 JSON 列无论怎么映射最后都得手工序列化。用 Map 把「列名 → 值」的对应写在 SQL 里，
 * 一眼能核对，列增删也只改这里。
 *
 * <p>写入用单条 {@code INSERT ... ON DUPLICATE KEY UPDATE}，靠 {@code uk_holland_student}
 * 唯一键落到同一行。不写成「先删后插」—— 那会在两步之间留下一段没有画像的窗口，
 * 任何模块此时读取都会拿到空态，违反 NFR-3 与 AC-3。
 *
 * <p>{@code revision} 在冲突分支里自增，表示「第几次生成」；首次插入固定为 1。
 */
@Mapper
public interface HollandProfileMapper {

    @Insert("""
            INSERT INTO holland_profile
              (student_id, score_r, score_i, score_a, score_s, score_e, score_c, top_dimension,
               recommend_dirs, intent_cities, expected_industry, skill_gaps, skill_missing,
               revision, generated_at)
            VALUES
              (#{studentId}, #{scoreR}, #{scoreI}, #{scoreA}, #{scoreS}, #{scoreE}, #{scoreC},
               #{topDimension}, #{recommendDirs}, #{intentCities}, #{expectedIndustry},
               #{skillGaps}, #{skillMissing}, 1, NOW())
            ON DUPLICATE KEY UPDATE
              score_r = VALUES(score_r),
              score_i = VALUES(score_i),
              score_a = VALUES(score_a),
              score_s = VALUES(score_s),
              score_e = VALUES(score_e),
              score_c = VALUES(score_c),
              top_dimension = VALUES(top_dimension),
              recommend_dirs = VALUES(recommend_dirs),
              intent_cities = VALUES(intent_cities),
              expected_industry = VALUES(expected_industry),
              skill_gaps = VALUES(skill_gaps),
              skill_missing = VALUES(skill_missing),
              revision = revision + 1,
              generated_at = NOW()
            """)
    int upsert(Map<String, Object> row);

    /** 按内部学生主键读一行；没有画像时返回 null。 */
    @Select("""
            SELECT student_id, score_r, score_i, score_a, score_s, score_e, score_c, top_dimension,
                   recommend_dirs, intent_cities, expected_industry, skill_gaps, skill_missing,
                   revision, generated_at
              FROM holland_profile
             WHERE student_id = #{studentId}
            """)
    Map<String, Object> findByStudentId(@Param("studentId") long studentId);
}
