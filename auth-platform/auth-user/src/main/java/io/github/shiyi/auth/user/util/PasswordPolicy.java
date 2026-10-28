package io.github.shiyi.auth.user.util;

import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;

/**
 * Password policy (development doc chapter 10):
 * >= 8 chars, at least 3 of 4 character classes (lower / upper / digit / special).
 * Applies to create and reset; history/expiry checks land with M2+.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new BizException(ErrorCode.BAD_REQUEST, "password must be 8-64 characters");
        }
        int classes = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) classes++;
        if (password.chars().anyMatch(Character::isUpperCase)) classes++;
        if (password.chars().anyMatch(Character::isDigit)) classes++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) classes++;
        if (classes < 3) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "password needs at least 3 of: lower, upper, digit, special");
        }
    }
}
