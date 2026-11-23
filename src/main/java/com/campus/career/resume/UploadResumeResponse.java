package com.campus.career.resume;

import java.util.List;

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
     * <p>本周只固定结构与「空数组不省略」的语义；元素内部的字段结构随解析链路一起定义，
     * 在接入大模型的那个 PR 里与 spec FR-5 对齐后再补类型，避免现在凭空造字段。
     */
    public record ParseStage(List<Object> education,
                             List<String> skills,
                             List<Object> projects,
                             List<Object> internships) {
    }
}
