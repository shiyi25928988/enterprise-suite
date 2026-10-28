package io.github.shiyi.auth.user.account;

/**
 * Minimal account view consumed by the security layer.
 *
 * @param id         user id
 * @param username   login name
 * @param password   encoded password (DelegatingPasswordEncoder format)
 * @param realName   display name
 * @param status     1 enabled 0 disabled 2 locked
 * @param mfaEnabled whether a TOTP second factor is required at login
 * @param totpSecret Base32 TOTP secret (only present when mfaEnabled)
 */
public record UserAccount(Long id, String username, String password, String realName,
                          Integer status, boolean mfaEnabled, String totpSecret) {
}
