package io.github.shiyi.auth.server.auth.mfa;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.server.auth.PlatformUser;
import io.github.shiyi.auth.server.auth.PlatformUserDetailsService;
import io.github.shiyi.auth.user.account.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * TOTP second factor: login completion plus enrollment management.
 *
 * <pre>
 * POST /api/auth/mfa/verify    {challengeId, code}  — finish a challenged login (anonymous)
 * GET  /api/auth/mfa/status                          — is MFA on for me?
 * POST /api/auth/mfa/setup                           — new secret + otpauth URI (pending, session-held)
 * POST /api/auth/mfa/confirm   {code}                — verify pending secret, switch MFA on
 * POST /api/auth/mfa/disable   {code}                — verify current code, switch MFA off
 * </pre>
 */
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    private static final String SESSION_PENDING_SECRET = "mfa.pending.secret";

    private final MfaChallengeService challenges;
    private final TotpService totp;
    private final UserAccountService accounts;
    private final PlatformUserDetailsService userDetailsService;

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public MfaController(MfaChallengeService challenges, TotpService totp,
                         UserAccountService accounts, PlatformUserDetailsService userDetailsService) {
        this.challenges = challenges;
        this.totp = totp;
        this.accounts = accounts;
        this.userDetailsService = userDetailsService;
    }

    // ---------- login completion ----------

    @PostMapping("/verify")
    public ResponseEntity<ApiResult<Map<String, String>>> verify(
            @RequestBody Map<String, String> body,
            HttpServletRequest request, HttpServletResponse response) {
        String challengeId = body.get("challengeId");
        String code = body.get("code");
        if (challengeId == null || code == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "challengeId and code are required");
        }

        var challenge = challenges.peek(challengeId)
                .orElseThrow(() -> new BizException(ErrorCode.MFA_CHALLENGE_EXPIRED));

        var account = accounts.findByUsername(challenge.username())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHENTICATED));

        if (!totp.verify(account.totpSecret(), code)) {
            challenges.recordFailure(challengeId);
            throw new BizException(ErrorCode.MFA_INVALID);
        }
        challenges.consume(challengeId);

        UserDetails user = userDetailsService.loadUserByUsername(account.username());
        accounts.onLoginSuccess("PASSWORD+TOTP", account.username(), account.id(),
                clientIp(request), request.getHeader("User-Agent"));

        // Both factors are proven at this point: password earlier, TOTP now.
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null,
                io.github.shiyi.auth.server.auth.AuthenticationFactors.of(
                        user,
                        io.github.shiyi.auth.server.auth.AuthenticationFactors.PASSWORD,
                        io.github.shiyi.auth.server.auth.AuthenticationFactors.TOTP));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        if (user instanceof io.github.shiyi.auth.server.auth.PlatformUser platformUser) {
            io.github.shiyi.auth.server.auth.LoginSessionSupport.stamp(request, accounts, platformUser);
        }

        return ResponseEntity.ok(ApiResult.ok(Map.of("redirect", "/")));
    }

    // ---------- enrollment ----------

    @GetMapping("/status")
    public ApiResult<Map<String, Object>> status() {
        PlatformUser me = currentUser();
        var account = accounts.findByUsername(me.getUsername()).orElseThrow();
        return ApiResult.ok(Map.of(
                "enabled", account.mfaEnabled(),
                "username", account.username()));
    }

    @PostMapping("/setup")
    public ApiResult<Map<String, String>> setup(HttpServletRequest request) {
        PlatformUser me = currentUser();
        String secret = totp.newSecret();
        request.getSession().setAttribute(SESSION_PENDING_SECRET, secret);
        String uri = totp.otpauthUri("Enterprise Suite", me.getUsername(), secret);
        return ApiResult.ok(Map.of("secret", secret, "otpauthUri", uri));
    }

    @PostMapping("/confirm")
    public ApiResult<Void> confirm(@RequestBody Map<String, String> body, HttpServletRequest request) {
        PlatformUser me = currentUser();
        HttpSession session = request.getSession(false);
        String pending = session == null ? null
                : (String) session.getAttribute(SESSION_PENDING_SECRET);
        if (pending == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "no pending TOTP setup, call /setup first");
        }
        if (!totp.verify(pending, body.get("code"))) {
            throw new BizException(ErrorCode.MFA_INVALID);
        }
        accounts.enableMfa(me.id(), pending);
        session.removeAttribute(SESSION_PENDING_SECRET);
        return ApiResult.ok();
    }

    @PostMapping("/disable")
    public ApiResult<Void> disable(@RequestBody Map<String, String> body) {
        PlatformUser me = currentUser();
        var account = accounts.findByUsername(me.getUsername()).orElseThrow();
        if (account.mfaEnabled() && !totp.verify(account.totpSecret(), body.get("code"))) {
            throw new BizException(ErrorCode.MFA_INVALID);
        }
        accounts.disableMfa(me.id());
        return ApiResult.ok();
    }

    // ---------- helpers ----------

    private PlatformUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof PlatformUser user)) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
