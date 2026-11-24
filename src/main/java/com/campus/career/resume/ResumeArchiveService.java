package com.campus.career.resume;

import com.campus.career.common.BusinessException;
import com.campus.career.common.ErrorCode;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * M1 第二步「确认入库」与「读取当前档案」的服务层，对应 docs/architecture.md 4.1.2、4.1.3。
 *
 * <p>为什么单独一个类而不并进 {@link ResumeParseService}：两者的输入来源不同。
 * 解析服务的输入是文件字节、要调大模型、要处理重试与降级；本服务的输入是学生已经改过的表单，
 * 全程不碰模型。合在一起会让「本端点不调用大模型，故不出现 504 / 502」（4.1.2 的说明）
 * 变成一句需要读代码才能确认的话。
 *
 * <h2>为什么确认时不读暂存区</h2>
 * <p>契约 4.1.2 写的是「以提交内容为准写入（AC-2）」。学生在确认页改过的内容只存在于前端表单里，
 * 暂存区存的是解析原始结果。若这里去读暂存区再合并，就得逐字段判断「哪个字段学生改过」，
 * 既做不准，也会让「以提交内容为准」变成一句空话。所以提交什么就写什么，暂存区不参与。
 *
 * <h2>失败与幂等</h2>
 * <p>判空在唯一一次写库之前，失败路径上根本没有写点，旧档案因此不会被碰到（AC-9 的第二半）。
 * 写入是按 {@code student_id} 的 upsert，重复点击确认只刷新同一份档案，不产生第二份有效档案（契约 5.1）。
 *
 * <p>确认成功后不清空暂存区：契约没有定义清理时机，而暂存区按 {@code student_id} 覆盖、
 * 下次上传自然覆盖，多一个「删除」动作只会多一个可能与正式档案不一致的状态。
 */
@Service
public class ResumeArchiveService {

    private final ConfirmedResumeRepository repository;

    public ResumeArchiveService(ConfirmedResumeRepository repository) {
        this.repository = repository;
    }

    /**
     * 把学生确认后的内容写入正式档案。
     *
     * @param studentId 学生标识，来自请求头，见 {@code StudentIdentity}
     * @param request   确认页提交的四类字段
     * @return 写入后的档案，供响应体回显状态与确认时间
     * @throws BusinessException {@code 422 NO_VALID_FIELD}：四类字段全部为空时
     */
    public ConfirmedResume confirm(String studentId, ConfirmResumeRequest request) {
        ParsedResume content = request.toParsedResume();

        // AC-8 的触发条件在确认页同样成立：学生可以把四类条目一条条删光再提交，
        // 这与「解析完成但没抽到内容」本质相同，共用同一个码，不自造第二个。
        if (content.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_VALID_FIELD);
        }

        ConfirmedResume resume = ConfirmedResume.of(content);
        repository.replace(studentId, resume);
        return resume;
    }

    /** 当前已确认档案；未建档时返回空，由 API 层转成 {@code data: null}。 */
    public Optional<ConfirmedResume> current(String studentId) {
        return repository.find(studentId);
    }
}
