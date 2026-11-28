package com.campus.career.job;

import java.util.List;
import java.util.Optional;

/**
 * M3 的演示岗位目录。
 *
 * <p>MVP1 不做自有岗位库、不抓取岗位正文（module-03 Non-Goals），岗位数据用常量提供，
 * 与 M2 的 {@code JobDirectionCatalog} 同理：没有运营可改的岗位表，也不需要建表。
 *
 * <p>目录固定包含 {@code J001}（外链可达）与 {@code J003}（外链失效），
 * 分别作为 AC-1 与 AC-3 的稳定演示数据。
 */
public final class JobCatalog {

    /** 外链失效后的同关键词重检入口。 */
    public static final String RETRY_SEARCH_BASE = "https://www.zhipin.com/web/geek/job?query=";

    private static final List<JobPosting> DEMO_JOBS = List.of(
            new JobPosting("J001", "Java 后端开发实习生",
                    List.of("Java", "SQL", "Spring Boot"),
                    "https://example.com/jobs/J001", true, null),
            new JobPosting("J002", "前端开发实习生",
                    List.of("Vue", "JavaScript"),
                    "https://example.com/jobs/J002", true, null),
            new JobPosting("J003", "数据分析实习生",
                    List.of("Python"),
                    "https://example.com/jobs/J003", true, "Python 数据分析 实习生"));

    private JobCatalog() {
    }

    public static List<JobPosting> all() {
        return DEMO_JOBS;
    }

    public static Optional<JobPosting> find(String jobId) {
        return DEMO_JOBS.stream()
                .filter(job -> job.jobId().equals(jobId))
                .findFirst();
    }
}
