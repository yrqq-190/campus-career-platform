package com.campus.career.job;

import com.campus.career.common.BusinessException;
import com.campus.career.common.ErrorCode;

/**
 * 岗位外链失效（AC-3）。
 *
 * <p>继承 {@link BusinessException}，保证即使本模块的 advice 未被应用，
 * common 的兜底也能返回 404 + {@code JOB_LINK_INVALID}；额外携带重检关键词，
 * 由 {@code JobExceptionAdvice} 补进响应体顶层。
 */
public class JobLinkInvalidException extends BusinessException {

    private final String retryKeyword;

    public JobLinkInvalidException(String retryKeyword) {
        super(ErrorCode.JOB_LINK_INVALID);
        this.retryKeyword = retryKeyword;
    }

    public String retryKeyword() {
        return retryKeyword;
    }
}
