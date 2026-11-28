package com.campus.career.job;

import com.campus.career.holland.profile.ResumeProfileSource;
import com.campus.career.holland.profile.ResumeSnapshot;
import com.campus.career.job.dto.JobLinkView;
import com.campus.career.job.dto.JobRecommendationItem;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * M3 的推荐与跳转编排，对应 module-03 AC-1 与 AC-3。
 *
 * <p>画像侧只读取技能：M2 的 {@code ProfileView} 不暴露 skills 字段，
 * 技能的唯一来源是已确认档案快照 {@link ResumeProfileSource}，这里只读不写，
 * 不改动 M2 的画像结构。
 */
@Service
public class JobRecommendationService {

    private final ResumeProfileSource resumeProfileSource;

    public JobRecommendationService(ResumeProfileSource resumeProfileSource) {
        this.resumeProfileSource = resumeProfileSource;
    }

    /**
     * 生成推荐列表（AC-1）。
     *
     * <p>只返回至少命中一项要求技能的岗位；一项都没命中的岗位不出现，
     * 因此画像没有技能时天然得到空列表，不额外造分支。
     * 排序按命中数降序、jobId 升序，保证同一份输入的结果稳定。
     */
    public List<JobRecommendationItem> recommend(String studentId) {
        List<String> skills = resumeProfileSource.findConfirmed(studentId)
                .map(ResumeSnapshot::skills)
                .orElse(List.of());

        record Matched(JobPosting job, List<String> hits) {
        }

        return JobCatalog.all().stream()
                .map(job -> new Matched(job, hitSkills(job, skills)))
                .filter(matched -> !matched.hits().isEmpty())
                .sorted(Comparator.comparingInt((Matched matched) -> matched.hits().size())
                        .reversed()
                        .thenComparing(matched -> matched.job().jobId()))
                .map(matched -> toItem(matched.job(), matched.hits()))
                .toList();
    }

    /**
     * 点击外链时返回跳转地址（FR-3）。
     *
     * @throws JobLinkInvalidException 外链已失效（AC-3）
     */
    public JobLinkView link(String jobId) {
        JobPosting job = JobCatalog.find(jobId)
                // 岗位不存在同样没有可跳转的外链，按失效处理，不新增 spec 之外的错误码
                .orElseThrow(() -> new JobLinkInvalidException(jobId));
        if (job.linkInvalid()) {
            throw new JobLinkInvalidException(job.retryKeyword());
        }
        return new JobLinkView(job.jobId(), job.sourceUrl());
    }

    private JobRecommendationItem toItem(JobPosting job, List<String> hits) {
        boolean invalid = job.linkInvalid();
        return new JobRecommendationItem(
                job.jobId(),
                job.title(),
                "命中技能：" + String.join("、", hits),
                job.sourceUrl(),
                job.demo(),
                invalid ? "INVALID" : "OK",
                invalid ? job.retryKeyword() : null);
    }

    private List<String> hitSkills(JobPosting job, List<String> skills) {
        return job.requiredSkills().stream()
                .filter(required -> containsSkill(skills, required))
                .toList();
    }

    /** 与 M2 一致：技能比对忽略大小写与首尾空白。 */
    private boolean containsSkill(List<String> skills, String target) {
        for (String skill : skills) {
            if (skill != null && skill.trim().equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
