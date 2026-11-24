package com.campus.career.holland.profile;

import com.campus.career.resume.ConfirmedResume;
import com.campus.career.resume.ConfirmedResumeRepository;
import com.campus.career.resume.ParsedResume;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用 M1 正式档案兑现 {@link ResumeProfileSource} 的适配器。
 *
 * <p>方向按 architecture 4.1.3 的越界声明摆：<b>M1 不向下游推送数据，M2 主动读取已确认档案</b>。
 * 所以适配器放在 M2 侧、依赖 M1 的 {@link ConfirmedResumeRepository}，反过来会形成 M1 → M2 的反向依赖。
 *
 * <h2>为什么现在不标 {@code @Component}</h2>
 * <p>{@link InMemoryResumeProfileSource} 已经是容器里的 {@code ResumeProfileSource} 实现。
 * 两个实现同时进容器，{@code ProfileService} 的构造器注入会直接歧义启动失败；
 * 标 {@code @Primary} 又会把画像的数据来源从 T5 的占位切走，让 T5 的 11 个用例失去数据。
 * 因此本类本周只作为「插座已经插得上」的证据存在，由 T3 的 AC-9 用例直接构造使用。
 * 等 T1 建表、确认入库端点（4.1.2）落地后，删掉 {@link InMemoryResumeProfileSource}、
 * 给本类补上 {@code @Component}，画像合成逻辑一行不用改。
 *
 * <h2>为什么只读「已确认」</h2>
 * <p>FR-6 写明「未确认的内容任何模块都不得读取」。{@link ConfirmedResumeRepository} 里只存确认过的档案，
 * 这里再按 {@code status} 过滤一次，是为了防住「有人把 {@code PENDING_CONFIRM} 也写进正式档案表」
 * 这类将来才会出现的错误——画像一旦读到未确认内容，AC-2 的整条链路就失去意义。
 */
public class ArchiveResumeProfileSource implements ResumeProfileSource {

    private final ConfirmedResumeRepository archive;

    public ArchiveResumeProfileSource(ConfirmedResumeRepository archive) {
        this.archive = archive;
    }

    @Override
    public Optional<ResumeSnapshot> findConfirmed(String studentId) {
        return archive.find(studentId)
                .filter(ConfirmedResume::isConfirmed)
                .map(resume -> {
                    ParsedResume content = resume.content();
                    return new ResumeSnapshot(content.skills(),
                            summarize(content.education()),
                            summarize(content.projects()),
                            summarize(content.internships()));
                });
    }

    /**
     * 把一条结构化条目压成一行可读文本。
     *
     * <p>按条目的值顺序拼接，不猜字段名：教育背景写 {@code school + major + degree}、
     * 项目经历写 {@code name + role} 这类映射一旦写死，就成了 M1 契约的第二份副本，
     * M1 加字段时这里必须同步改，否则静默丢字段。画像只用这些文本作理由的补充说明，
     * 不参与打分，拼接顺序与格式都不影响结论。
     */
    private static List<String> summarize(List<Map<String, Object>> entries) {
        return entries.stream()
                .map(entry -> entry.values().stream()
                        .filter(value -> value != null)
                        .map(String::valueOf)
                        .filter(text -> !text.isBlank())
                        .collect(Collectors.joining(" ")))
                .filter(text -> !text.isBlank())
                .toList();
    }
}
