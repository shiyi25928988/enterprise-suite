package io.github.shiyi.auth.server.auth.dingtalk;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * DingTalk scan-login endpoints for the SPA (M5c):
 *
 * <ul>
 *   <li>GET  /api/auth/dingtalk/config — QR-login launch parameters (empty when disabled)</li>
 *   <li>POST /api/auth/dingtalk/login  — {code} from the DingTalk redirect; establishes the session</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth/dingtalk")
public class DingTalkAuthController {

    private final DingTalkProperties props;
    private final AuthenticationManager authenticationManager;
    private final io.github.shiyi.auth.user.account.UserAccountService accounts;

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public DingTalkAuthController(DingTalkProperties props,
                                  AuthenticationManager authenticationManager,
                                  io.github.shiyi.auth.user.account.UserAccountService accounts) {
        this.props = props;
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
    }

    @GetMapping("/config")
    public ApiResult<Map<String, Object>> config() {
        if (!props.isEnabled()) {
            return ApiResult.ok(Map.of("enabled", false));
        }
        return ApiResult.ok(Map.of(
                "enabled", true,
                "appKey", props.getAppKey() == null ? "" : props.getAppKey(),
                "redirectUri", props.getRedirectUri() == null ? "" : props.getRedirectUri()));
    }

    /** body: {"code": "..."} — the OAuth code DingTalk appended to the redirect URI. */
    @PostMapping("/login")
    public ResponseEntity<ApiResult<Map<String, String>>> login(
            @RequestBody Map<String, String> body,
            HttpServletRequest request, HttpServletResponse response) {
        if (!props.isEnabled()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "DingTalk login is not enabled");
        }
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "missing code");
        }

        var token = new DingTalkAuthenticationToken(code);
        // M5c: audit rows and anomaly rules see the proxied client IP, not ours
        token.setDetails(new ForwardedAwareWebAuthenticationDetails(request));
        Authentication authentication = authenticationManager.authenticate(token);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        if (authentication.getPrincipal() instanceof io.github.shiyi.auth.server.auth.PlatformUser user) {
            io.github.shiyi.auth.server.auth.LoginSessionSupport.stamp(request, accounts, user);
        }

        return ResponseEntity.ok(ApiResult.ok(Map.of("redirect", "/")));
    }
}
