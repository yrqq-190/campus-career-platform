package com.campus.career.common;

/**
 * 业务异常：只承载 spec 与接口契约里已定义的错误码。
 *
 * <p>抛这个异常的地方必须是服务层的业务判定，不用于参数格式错误。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
