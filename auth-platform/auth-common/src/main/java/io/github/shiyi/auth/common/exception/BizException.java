package io.github.shiyi.auth.common.exception;

import io.github.shiyi.auth.common.api.ErrorCode;

/**
 * Business exception carrying an {@link ErrorCode}; translated to
 * {@code ApiResult} by the global exception handler in the web layer.
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
