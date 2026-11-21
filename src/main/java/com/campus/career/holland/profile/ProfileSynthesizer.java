package com.campus.career.holland.profile;

import com.campus.career.holland.HollandDimension;
import com.campus.career.holland.HollandScorer;
import com.campus.career.holland.profile.dto.DirectionView;
import com.campus.career.holland.profile.dto.ProfileView;
import com.campus.career.holland.profile.dto.SkillGapMissing;
import com.campus.career.holland.profile.dto.SkillGapView;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 画像合成规则，对应 spec/module-02 FR-5 ～ FR-8 与 AC-2、AC-3、AC-5、AC-8。
 *
 * <p>纯规则计算，不调大模型（NFR-1），不读写任何存储，方便单测直接喂数据。
 *
 * <h2>方向打分</h2>
 * 每个方向有主导维度与要求技能，综合分：
 *
 * <pre>
 *   score = 主导维度分数 * 0.6 + 技能命中率 * 100 * 0.4
 * </pre>
 *
 * <p>兴趣占 6 成、技能占 4 成。理由：M2 的定位是「兴趣画像」，兴趣是主信号；
 * 但如果学生已经具备某方向的技能，这个方向更值得优先推荐，所以技能要给权重。
 * 技能端用「命中率」而不是「命中个数」，避免要求技能多的方向天然吃亏。
 *
 * <h2>方向选取</h2>
 * 按综合分降序，<b>每个兴趣维度最多取一个方向</b>，取满 4 个为止。
 * 不加这条限制的话，I 维度特别高的学生可能拿到「后端开发 + 数据分析」两个同质方向，
 * 另外两个维度一分不给，推荐就失去参考价值了。
 *
 * <h2>能力短板</h2>
 * 取这 4 个方向的全部要求技能，减去学生已有技能，按方向顺序去重后最多保留 5 项
 * （AC-2 第 4 条）。技能为空时返回 {@link SkillGapMissing} 而不是空数组。
 */
@Component
public class ProfileSynthesizer {

    /** 兴趣权重与技能权重，和为 1。 */
    private static final double INTEREST_WEIGHT = 0.6;
    private static final double SKILL_WEIGHT = 0.4;

    /** AC-2 要求能力短板不超过 5 项。 */
    static final int MAX_SKILL_GAPS = 5;

    /**
     * 合成画像。
     *
     * @param result 计分结果，含六维分数与可追溯明细
     * @param resume 已确认档案；未确认过简历时为空，走 AC-5 降级
     * @param intent 求职意向，未填写时城市与行业为空数组
     * @throws ScoreNotTraceableException 任一分数的来源不完整（AC-8）
     */
    public ProfileView synthesize(HollandScorer.Result result,
                                  Optional<ResumeSnapshot> resume,
                                  StudentIntent intent) {
        // AC-8：画像对外提供之前先做追溯校验，不完整的分数不允许被拿去生成推荐
        if (!result.allTraceable()) {
            throw new ScoreNotTraceableException("六维得分无法完整回溯到原始作答，本次画像不对外提供");
        }

        List<String> skills = resume.map(ResumeSnapshot::skills).orElse(List.of());
        boolean skillMissing = resume.isEmpty() || resume.get().hasNoSkills();

        List<JobDirection> picked = pickDirections(result.scores(), skills);
        List<DirectionView> directions = new ArrayList<>(picked.size());
        for (JobDirection direction : picked) {
            directions.add(new DirectionView(direction.name(),
                    reasonOf(direction, result.scores(), skills, resume.isEmpty())));
        }

        Object skillGaps = skillMissing
                ? SkillGapMissing.of()
                : gapsOf(picked, skills);

        return new ProfileView(directions, intent.cities(), intent.industries(), skillGaps);
    }

    private List<JobDirection> pickDirections(Map<HollandDimension, Integer> scores, List<String> skills) {
        record Scored(JobDirection direction, double score) {
        }

        List<Scored> ranked = JobDirectionCatalog.all().stream()
                .map(d -> new Scored(d, scoreOf(d, scores, skills)))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();

        List<JobDirection> picked = new ArrayList<>(JobDirectionCatalog.RECOMMEND_COUNT);
        Set<HollandDimension> usedDimensions = EnumSet.noneOf(HollandDimension.class);
        for (Scored scored : ranked) {
            if (picked.size() >= JobDirectionCatalog.RECOMMEND_COUNT) {
                break;
            }
            if (usedDimensions.contains(scored.direction().primary())) {
                continue;
            }
            picked.add(scored.direction());
            usedDimensions.add(scored.direction().primary());
        }
        return picked;
    }

    private double scoreOf(JobDirection direction, Map<HollandDimension, Integer> scores, List<String> skills) {
        int interest = scores.getOrDefault(direction.primary(), 0);
        long hits = direction.requiredSkills().stream().filter(skill -> containsSkill(skills, skill)).count();
        double hitRate = direction.requiredSkills().isEmpty()
                ? 0
                : (double) hits / direction.requiredSkills().size();
        return interest * INTEREST_WEIGHT + hitRate * 100 * SKILL_WEIGHT;
    }

    /**
     * 支撑理由必须说清依据哪几个维度或哪几项技能（AC-2 第 2 条），所以分三种情况写，
     * 不能只丢一句「匹配度高」。
     */
    private String reasonOf(JobDirection direction,
                            Map<HollandDimension, Integer> scores,
                            List<String> skills,
                            boolean resumeMissing) {
        int interest = scores.getOrDefault(direction.primary(), 0);
        String base = "%s 维度 %d 分".formatted(direction.primary().name(), interest);

        if (resumeMissing) {
            return base + "，简历尚未确认，技能暂未参与匹配";
        }
        List<String> hit = direction.requiredSkills().stream()
                .filter(skill -> containsSkill(skills, skill))
                .toList();
        if (hit.isEmpty()) {
            return base + "，档案中暂无该方向所需技能";
        }
        return base + "，已具备 " + String.join("、", hit) + " 技能";
    }

    private List<SkillGapView> gapsOf(List<JobDirection> picked, List<String> skills) {
        Set<String> missing = new LinkedHashSet<>();
        List<SkillGapView> gaps = new ArrayList<>();
        for (JobDirection direction : picked) {
            for (String required : direction.requiredSkills()) {
                if (gaps.size() >= MAX_SKILL_GAPS) {
                    return gaps;
                }
                if (containsSkill(skills, required) || !missing.add(required)) {
                    continue;
                }
                gaps.add(new SkillGapView(required,
                        direction.name() + "方向要求该技能，简历中未体现"));
            }
        }
        return gaps;
    }

    /** 技能比对忽略大小写与首尾空白，避免学生填「java」时被当成没有 Java。 */
    private boolean containsSkill(List<String> skills, String target) {
        for (String skill : skills) {
            if (skill != null && skill.trim().equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
