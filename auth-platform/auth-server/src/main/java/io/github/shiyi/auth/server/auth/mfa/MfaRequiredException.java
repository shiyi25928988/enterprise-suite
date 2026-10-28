package io.github.shiyi.auth.server.auth.mfa;

import org.springframework.security.core.AuthenticationException;

/**
 * Password verified but a TOTP code is still required. Carries the challenge id
 * the client must echo back to /api/auth/mfa/verify.
 */
public class MfaRequiredException extends AuthenticationException {

    private final String challengeId;

    public MfaRequiredException(String challengeId) {
        super("Second-factor code required");
        this.challengeId = challengeId;
    }

    public String getChallengeId() {
        return challengeId;
    }
}
