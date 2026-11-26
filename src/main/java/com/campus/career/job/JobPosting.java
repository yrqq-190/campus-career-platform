package com.campus.career.job;

import java.util.List;

/**
 * 演示岗位目录中的一条岗位，只承载外链跳转需要的元数据。
 *
 * <p>MVP1 不自建岗位库（module-03 Non-Goals），这里不存岗位正文，只保留
 * 岗位名、要求技能与外链。外链是否失效由演示数据直接标出，不做实时抓取。
 *
 * @param jobId         岗位标识
 * @param title         岗位名称
 * @param requiredSkills 岗位要求技能，用于生成推荐理由
 * @param sourceUrl     外部招聘链接
 * @param demo          是否为演示数据（AC-4）
 * @param retryKeyword  外链失效时的同关键词重检词；外链可达时为 null（AC-3）
 */
public record JobPosting(String jobId,
                         String title,
                         List<String> requiredSkills,
                         String sourceUrl,
                         boolean demo,
                         String retryKeyword) {

    public JobPosting {
        requiredSkills = requiredSkills == null ? List.of() : List.copyOf(requiredSkills);
    }

    /** 外链是否已失效。 */
    public boolean linkInvalid() {
        return retryKeyword != null && !retryKeyword.isBlank();
    }
}
