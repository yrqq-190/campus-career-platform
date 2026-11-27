package com.campus.career.holland.profile;

import com.campus.career.common.StudentDirectory;
import com.campus.career.holland.HollandDimension;
import com.campus.career.holland.profile.dto.DirectionView;
import com.campus.career.holland.profile.dto.ProfileView;
import com.campus.career.holland.profile.dto.SkillGapMissing;
import com.campus.career.holland.profile.dto.SkillGapView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * {@link ProfileRepository} 的数据库实现，只在 mysql profile 下装配。
 *
 * <p>写入是单条 {@code INSERT ... ON DUPLICATE KEY UPDATE}，语义与内存实现的
 * {@code ConcurrentHashMap.put} 等价：读者要么看到旧画像，要么看到新画像，
 * 不存在「两份并存」或「没有画像」的中间态（AC-3、NFR-3）。
 *
 * <h2>三处列宽/类型上的妥协，都是有原因的</h2>
 * <ol>
 *   <li><b>{@code expected_industry} 是 {@code VARCHAR(128)}</b>，而 spec 的 A1 要求期望行业以数组返回。
 *       这里把数组序列化成 JSON 字符串塞进这一列（例如 {@code ["互联网"]}），读时再还原成 List。
 *       等 {@code spec/module-02} 的 A1 与 T1 的列类型改动落地，这一列应改成 JSON，届时只需改本类。
 *   <li><b>{@code skill_gaps} 有两种形态</b>：正常是短板数组，技能缺失时是降级标记对象。
 *       落库时数组进 {@code skill_gaps} JSON 列、{@code skill_missing} 置 1，读时按
 *       {@code skill_missing} 决定还原成哪种，不靠猜 JSON 的形状。
 *   <li><b>{@code student_id} 是 BIGINT 外键</b>，而 Service 传进来的是字符串学生标识。
 *       中间必须过 {@link StudentDirectory} 换成 {@code student.id}，见那个接口的说明。
 * </ol>
 */
@Repository
@Profile("mysql")
public class MybatisProfileRepository implements ProfileRepository {

    private static final TypeReference<List<DirectionView>> DIRECTIONS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };
    private static final TypeReference<List<SkillGapView>> SKILL_GAPS = new TypeReference<>() {
    };

    /** {@code expected_industry} 的列宽，写入前自检，超长直接报错而不是让 MySQL 截断。 */
    private static final int EXPECTED_INDUSTRY_MAX = 128;

    private final HollandProfileMapper profileMapper;
    private final StudentDirectory studentDirectory;
    private final ObjectMapper objectMapper;

    public MybatisProfileRepository(HollandProfileMapper profileMapper,
                                    StudentDirectory studentDirectory,
                                    ObjectMapper objectMapper) {
        this.profileMapper = profileMapper;
        this.studentDirectory = studentDirectory;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ProfileView> find(String studentId) {
        return studentDirectory.findInternalId(studentId)
                .map(profileMapper::findByStudentId)
                .map(this::toView);
    }

    @Override
    public void replace(String studentId, ProfileView profile, Map<HollandDimension, Integer> scores) {
        long internalId = studentDirectory.findInternalId(studentId)
                .orElseThrow(() -> new IllegalStateException(
                        "学生标识 " + studentId + " 在 student 表里没有对应学号。"
                                + "落库要求请求头的值就是 student_no，演示数据见 db/seed.sql"));

        Map<String, Object> row = new HashMap<>();
        row.put("studentId", internalId);
        for (HollandDimension dimension : HollandDimension.values()) {
            row.put("score" + dimension.name(), scoreOf(scores, dimension));
        }
        row.put("topDimension", topDimension(scores));
        row.put("recommendDirs", writeJson(profile.directions()));
        row.put("intentCities", writeJson(profile.cities()));
        row.put("expectedIndustry", writeExpectedIndustry(profile.industries()));
        row.put("skillGaps", writeJson(profile.skillGaps()));
        row.put("skillMissing", profile.skillGaps() instanceof SkillGapMissing ? 1 : 0);

        profileMapper.upsert(row);
    }

    private ProfileView toView(Map<String, Object> row) {
        boolean skillMissing = asInt(row.get("skill_missing")) == 1;
        Object skillGaps = skillMissing
                ? SkillGapMissing.of()
                : readJson(row.get("skill_gaps"), SKILL_GAPS);
        return new ProfileView(
                readJson(row.get("recommend_dirs"), DIRECTIONS),
                readJson(row.get("intent_cities"), STRINGS),
                readJson(row.get("expected_industry"), STRINGS),
                skillGaps);
    }

    /**
     * 六维分数里取最高的一维，落 {@code top_dimension}。
     *
     * <p>同分时按枚举声明顺序取前一个，结果稳定可复现 —— 用 {@code HashMap} 的遍历顺序会让
     * 同一份作答在两次生成后 {@code top_dimension} 不同，看起来像数据错乱。
     */
    private String topDimension(Map<HollandDimension, Integer> scores) {
        return java.util.Arrays.stream(HollandDimension.values())
                .max(Comparator.comparingInt(dimension -> scoreOf(scores, dimension)))
                .orElseThrow(() -> new IllegalArgumentException("scores 为空，无法确定 top_dimension"))
                .name();
    }

    private int scoreOf(Map<HollandDimension, Integer> scores, HollandDimension dimension) {
        if (scores == null) {
            return 0;
        }
        return scores.getOrDefault(dimension, 0);
    }

    private String writeExpectedIndustry(List<String> industries) {
        String json = writeJson(industries);
        if (json != null && json.length() > EXPECTED_INDUSTRY_MAX) {
            throw new IllegalStateException(
                    "期望行业序列化后 " + json.length() + " 字符，超出 expected_industry 列宽 "
                            + EXPECTED_INDUSTRY_MAX + "：" + json);
        }
        return json;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("画像字段无法序列化为 JSON：" + value, e);
        }
    }

    private <T> T readJson(Object raw, TypeReference<T> type) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw.toString(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("holland_profile 的 JSON 列无法解析：" + raw, e);
        }
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(value.toString());
    }
}
