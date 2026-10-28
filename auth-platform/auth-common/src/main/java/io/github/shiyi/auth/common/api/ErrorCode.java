package io.github.shiyi.auth.common.api;

/**
 * Unified API error codes.
 *
 * <p>Contract rules: codes are stable once published to frontend teams.
 * Adding new codes is allowed; changing/removing requires review.</p>
 */
public enum ErrorCode {

    OK(0, "ok"),

    BAD_REQUEST(40001, "Invalid request parameters"),
    UNAUTHENTICATED(40100, "Not authenticated or session expired"),
    BAD_CREDENTIALS(40101, "Invalid username or password"),
    ACCOUNT_LOCKED(40102, "Account is locked"),
    PASSWORD_EXPIRED(40103, "Password expired, change required"),
    FORBIDDEN(40300, "Access denied"),
    NOT_FOUND(40400, "Resource not found"),
    CONFLICT(40900, "Resource conflict"),
    INTERNAL_ERROR(50000, "Internal server error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
