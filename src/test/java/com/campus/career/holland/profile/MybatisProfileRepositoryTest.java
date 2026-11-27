package com.campus.career.holland.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campus.career.common.StudentDirectory;
import com.campus.career.holland.HollandDimension;
import com.campus.career.holland.profile.dto.DirectionView;
import com.campus.career.holland.profile.dto.ProfileView;
import com.campus.career.holland.profile.dto.SkillGapMissing;
import com.campus.career.holland.profile.dto.SkillGapView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link MybatisProfileRepository} 的映射逻辑测试。
 *
 * <p>不打数据库：Mapper 与学生目录都用 mock，只验「Java 对象 → 行」与「行 → Java 对象」
 * 这两个方向的转换是否正确，包括 JSON 列的序列化、{@code skill_missing} 与
 * {@code top_dimension} 的取值。SQL 本身由 {@code HollandProfileMapperSqlTest} 单独验。
 *
 * <p>这个类不依赖 mysq profile，也不受 {@code @Profile("mysql")} 影响 —— 直接 new 出实例。
 */
class MybatisProfileRepositoryTest {

    private static final long INTERNAL_ID = 7L;
    private static final String EXTERNAL_ID = "2023001";

    private HollandProfileMapper mapper;
    private StudentDirectory directory;
    private MybatisProfileRepository repository;

    @BeforeEach
    void setUp() {
        mapper = mock(HollandProfileMapper.class);
        directory = mock(StudentDirectory.class);
        repository = new MybatisProfileRepository(mapper, directory, new ObjectMapper());
    }

    @Test
    @DisplayName("replace：六维分数与 top_dimension 写进行里，学生标识先换成内部主键")
    void replaceWritesScoresAndTopDimension() {
        when(directory.findInternalId(EXTERNAL_ID)).thenReturn(Optional.of(INTERNAL_ID));

        Map<HollandDimension, Integer> scores = new EnumMap<>(HollandDimension.class);
        scores.put(HollandDimension.R, 30);
        scores.put(HollandDimension.I, 88);
        scores.put(HollandDimension.A, 41);
        scores.put(HollandDimension.S, 55);
        scores.put(HollandDimension.E, 12);
        scores.put(HollandDimension.C, 66);

        repository.replace(EXTERNAL_ID, profileWithGaps(), scores);

        Map<String, Object> row = captureRow();
        assertThat(row.get("studentId")).isEqualTo(INTERNAL_ID);
        assertThat(row.get("scoreR")).isEqualTo(30);
        assertThat(row.get("scoreI")).isEqualTo(88);
        assertThat(row.get("scoreC")).isEqualTo(66);
        assertThat(row.get("topDimension")).isEqualTo("I");
        assertThat(row.get("skillMissing")).isEqualTo(0);
    }

    @Test
    @DisplayName("replace：同分时 top_dimension 取枚举声明顺序靠前的那一维，结果稳定")
    void topDimensionIsStableOnTie() {
        when(directory.findInternalId(EXTERNAL_ID)).thenReturn(Optional.of(INTERNAL_ID));

        Map<HollandDimension, Integer> scores = new EnumMap<>(HollandDimension.class);
        scores.put(HollandDimension.C, 90);
        scores.put(HollandDimension.R, 90);

        repository.replace(EXTERNAL_ID, profileWithGaps(), scores);

        assertThat(captureRow().get("topDimension")).isEqualTo("R");
    }

    @Test
    @DisplayName("replace：缺维度按 0 写入，不抛空指针")
    void missingDimensionFallsBackToZero() {
        when(directory.findInternalId(EXTERNAL_ID)).thenReturn(Optional.of(INTERNAL_ID));

        Map<HollandDimension, Integer> scores = new EnumMap<>(HollandDimension.class);
        scores.put(HollandDimension.A, 60);

        repository.replace(EXTERNAL_ID, profileWithGaps(), scores);

        Map<String, Object> row = captureRow();
        assertThat(row.get("scoreR")).isEqualTo(0);
        assertThat(row.get("topDimension")).isEqualTo("A");
    }

    @Test
    @DisplayName("replace：技能缺失时 skill_missing 置 1，skill_gaps 写降级标记")
    void skillMissingFlagIsWritten() {
        when(directory.findInternalId(EXTERNAL_ID)).thenReturn(Optional.of(INTERNAL_ID));

        ProfileView degraded = new ProfileView(
                List.of(new DirectionView("后端开发", "研究型与常规型得分靠前")),
                List.of("杭州"),
                List.of("互联网"),
                SkillGapMissing.of());

        repository.replace(EXTERNAL_ID, degraded, onlyR());

        Map<String, Object> row = captureRow();
        assertThat(row.get("skillMissing")).isEqualTo(1);
        assertThat(row.get("skillGaps").toString()).contains("PROFILE_SKILL_MISSING");
    }

    @Test
    @DisplayName("replace：期望行业序列化后超过列宽时直接报错，不让 MySQL 静默截断")
    void expectedIndustryTooLongFails() {
        when(directory.findInternalId(EXTERNAL_ID)).thenReturn(Optional.of(INTERNAL_ID));

        ProfileView tooLong = new ProfileView(
                List.of(),
                List.of(),
                List.of("互".repeat(130)),
                List.of());

        assertThatThrownBy(() -> repository.replace(EXTERNAL_ID, tooLong, onlyR()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("超出 expected_industry 列宽");
    }

    @Test
    @DisplayName("replace：学号在 student 表里查不到时报错，不静默兜底")
    void unknownStudentFails() {
        when(directory.findInternalId(EXTERNAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repository.replace(EXTERNAL_ID, profileWithGaps(), onlyR()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有对应学号");
    }

    @Test
    @DisplayName("find：没有画像时返回空")
    void findReturnsEmptyWhenNoRow() {
        when(directory.findInternalId(EXTERNAL_ID)).thenReturn(Optional.of(INTERNAL_ID));
        when(mapper.findByStudentId(INTERNAL_ID)).thenReturn(null);

        assertThat(repository.find(EXTERNAL_ID)).isEmpty();
    }

    @Test
    @DisplayName("find：学号查不到时返回空，不抛异常")
    void findReturnsEmptyWhenStudentUnknown() {
        when(directory.findInternalId("nobody")).thenReturn(Optional.empty());

        assertThat(repository.find("nobody")).isEmpty();
    }

    @Test
    @DisplayName("find：JSON 列原样还原成四项，skill_missing=1 时还原成降级标记")
    void findRestoresProfile() {
        when(directory.findInternalId(EXTERNAL_ID)).thenReturn(Optional.of(INTERNAL_ID));
        when(mapper.findByStudentId(INTERNAL_ID)).thenReturn(row(
                "[{\"name\":\"后端开发\",\"reason\":\"研究型与常规型得分靠前\"}]",
                "[\"杭州\",\"苏州\"]",
                "[\"互联网\"]",
                "{\"status\":\"PROFILE_SKILL_MISSING\"}",
                1));

        ProfileView view = repository.find(EXTERNAL_ID).orElseThrow();

        assertThat(view.directions()).singleElement()
                .satisfies(direction -> {
                    assertThat(direction.name()).isEqualTo("后端开发");
                    assertThat(direction.reason()).isEqualTo("研究型与常规型得分靠前");
                });
        assertThat(view.cities()).containsExactly("杭州", "苏州");
        assertThat(view.industries()).containsExactly("互联网");
        assertThat(view.skillGaps()).isInstanceOf(SkillGapMissing.class);
    }

    @Test
    @DisplayName("find：skill_missing=0 时还原成短板数组")
    void findRestoresSkillGapsArray() {
        when(directory.findInternalId(EXTERNAL_ID)).thenReturn(Optional.of(INTERNAL_ID));
        when(mapper.findByStudentId(INTERNAL_ID)).thenReturn(row(
                "[]", "[]", "[]",
                "[{\"skill\":\"Redis\",\"reason\":\"岗位普遍要求缓存经验\"}]",
                0));

        ProfileView view = repository.find(EXTERNAL_ID).orElseThrow();

        assertThat(view.skillGaps()).isInstanceOf(List.class);
        assertThat((List<?>) view.skillGaps()).singleElement()
                .isInstanceOf(SkillGapView.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureRow() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).upsert(captor.capture());
        return captor.getValue();
    }

    private Map<String, Object> row(String recommendDirs, String intentCities,
                                    String expectedIndustry, String skillGaps, int skillMissing) {
        Map<String, Object> row = new HashMap<>();
        row.put("student_id", INTERNAL_ID);
        row.put("recommend_dirs", recommendDirs);
        row.put("intent_cities", intentCities);
        row.put("expected_industry", expectedIndustry);
        row.put("skill_gaps", skillGaps);
        row.put("skill_missing", skillMissing);
        return row;
    }

    private ProfileView profileWithGaps() {
        return new ProfileView(
                List.of(new DirectionView("后端开发", "研究型与常规型得分靠前")),
                List.of("杭州", "苏州"),
                List.of("互联网"),
                List.of(new SkillGapView("Redis", "岗位普遍要求缓存经验")));
    }

    private Map<HollandDimension, Integer> onlyR() {
        Map<HollandDimension, Integer> scores = new EnumMap<>(HollandDimension.class);
        scores.put(HollandDimension.R, 70);
        return scores;
    }
}
