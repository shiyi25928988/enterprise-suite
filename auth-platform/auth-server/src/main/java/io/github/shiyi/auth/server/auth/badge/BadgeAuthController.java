package io.github.shiyi.auth.server.auth.badge;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
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
import io.github.shiyi.auth.server.auth.ForwardedAwareWebAuthenticationDetails;
import io.github.shiyi.auth.server.auth.PlatformUser;
import io.github.shiyi.auth.server.auth.LoginSessionSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Badge (工牌) login endpoints (M5c):
 *
 * <ul>
 *   <li>POST /api/auth/badge/login  — {badgeCode} scanned from the printed QR;
 *       public, establishes the session without a password</li>
 *   <li>POST /api/auth/badge/rotate — issue a fresh badge code (authenticated;
 *       the printed QR carries the code, rotation kills a lost badge)</li>
 *   <li>GET  /api/auth/badge/mine   — current code for re-rendering the QR</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth/badge")
public class BadgeAuthController {

    private final AuthenticationManager authenticationManager;
    private final io.github.shiyi.auth.user.account.UserAccountService accounts;

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public BadgeAuthController(AuthenticationManager authenticationManager,
                               io.github.shiyi.auth.user.account.UserAccountService accounts) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
    }

    /** body: {"badgeCode": "..."} — the secret encoded in the badge QR. */
    @PostMapping("/login")
    public ResponseEntity<ApiResult<Map<String, String>>> login(
            @RequestBody Map<String, String> body,
            HttpServletRequest request, HttpServletResponse response) {
        String badgeCode = body.get("badgeCode");
        if (badgeCode == null || badgeCode.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "missing badgeCode");
        }

        var token = new BadgeAuthenticationToken(badgeCode.trim());
        token.setDetails(new ForwardedAwareWebAuthenticationDetails(request));
        Authentication authentication = authenticationManager.authenticate(token);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        if (authentication.getPrincipal() instanceof PlatformUser user) {
            LoginSessionSupport.stamp(request, accounts, user);
        }

        return ResponseEntity.ok(ApiResult.ok(Map.of("redirect", "/")));
    }

    /** Replaces the caller's badge code; the old QR stops working immediately. */
    @PostMapping("/rotate")
    public ApiResult<Map<String, String>> rotate(@AuthenticationPrincipal PlatformUser user) {
        String code = accounts.rotateBadgeCode(user.id());
        return ApiResult.ok(Map.of("badgeCode", code));
    }

    @GetMapping("/mine")
    public ApiResult<Map<String, String>> mine(@AuthenticationPrincipal PlatformUser user) {
        String code = accounts.findBadgeCode(user.id());
        return ApiResult.ok(Map.of("badgeCode", code == null ? "" : code));
    }
}
