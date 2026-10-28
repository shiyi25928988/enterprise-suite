package io.github.shiyi.auth.common.api;

import java.util.UUID;

/**
 * Unified API response envelope.
 *
 * <pre>{@code
 * { "code": 0, "message": "ok", "data": { ... }, "traceId": "b7f3..." }
 * }</pre>
 *
 * @param <T> payload type
 */
public record ApiResult<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(ErrorCode.OK.code(), ErrorCode.OK.message(), data, newTraceId());
    }

    public static ApiResult<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> fail(ErrorCode errorCode) {
        return new ApiResult<>(errorCode.code(), errorCode.message(), null, newTraceId());
    }

    public static <T> ApiResult<T> fail(ErrorCode errorCode, String message) {
        return new ApiResult<>(errorCode.code(), message, null, newTraceId());
    }

    /** Failure carrying a payload (e.g. MFA_REQUIRED → {challengeId}). */
    public static <T> ApiResult<T> fail(ErrorCode errorCode, T data) {
        return new ApiResult<>(errorCode.code(), errorCode.message(), data, newTraceId());
    }

    public boolean isSuccess() {
        return code == ErrorCode.OK.code();
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
