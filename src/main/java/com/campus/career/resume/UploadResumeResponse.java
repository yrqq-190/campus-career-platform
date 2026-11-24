package com.campus.career.resume;

import java.util.List;
import java.util.Map;

/**
 * {@code POST /api/v1/resumes} 的成功响应体，结构照抄 docs/architecture.md 4.1.1 的示例。
 *
 * @param resumeId 本次上传产生的简历标识，格式 {@code r_yyyyMMdd_序号}。本周未落库，
 *                 序号为进程内生成的占位值；T1 建表合并后改为由库生成。
 * @param status   固定 {@code PENDING_CONFIRM}，表示结果只进暂存区、正式档案未变（AC-1）
 * @param parse    四类字段，恒存在。简历中没有的类别返回空数组而非省略（AC-4）
 */
public record UploadResumeResponse(String resumeId, String status, ParseStage parse) {

    /**
     * 解析结果的四类字段。
     *
     * <p>元素内部保留模型输出经过 Schema 校验后的键值，不再削成固定 POJO：
     * spec FR-5 只要求「每类按统一字段结构返回」，没有把每类的字段清单定死，
     * 提前固化等于自造契约。强制必填的键见 {@link ResumeParseSchema}。
     */
    public record ParseStage(List<Map<String, Object>> education,
                            List<String> skills,
                            List<Map<String, Object>> projects,
                            List<Map<String, Object>> internships) {
    }
}
