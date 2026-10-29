package io.github.shiyi.auth.user.web;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

import io.github.shiyi.auth.user.entity.SysOperationLog;
import io.github.shiyi.auth.user.service.OperationLogService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Records every write request against the admin API in
 * {@code sys_operation_log}. Read-only by design: bodies are never stored
 * (they may carry passwords / client secrets) — method + URI + semantic
 * action + result status is enough for an audit trail, authentication is
 * already covered by sys_login_log.
 *
 * <p>Registered as a plain {@link OncePerRequestFilter} bean so Boot applies
 * it to every security chain; only /api/v1/** and the self-service password
 * change pass the shouldNotFilter gate. Recording happens after the chain so
 * the response status reflects the real outcome (409/403 included — a denied
 * attempt is exactly what an auditor wants to see).</p>
 */
public class OperationAuditFilter extends OncePerRequestFilter {

    /** page size cap duplicated from OperationLogService to avoid a cycle */
    private final OperationLogService logs;

    public OperationAuditFilter(OperationLogService logs) {
        this.logs = logs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/") || "/api/auth/password".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            record(request, response);
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return; // unauthenticated writes are 401 noise, not management actions
        }
        SysOperationLog entry = new SysOperationLog();
        entry.setOperator(auth.getName());
        entry.setMethod(request.getMethod());
        String uri = request.getRequestURI();
        entry.setUri(uri.length() > 500 ? uri.substring(0, 500) : uri);
        entry.setAction(resolveAction(request.getMethod(), uri));
        entry.setStatusCode(response.getStatus());
        entry.setIp(clientIp(request));
        entry.setCreateTime(LocalDateTime.now());
        try {
            logs.record(entry);
        } catch (RuntimeException ex) {
            // auditing must never break the actual request
            org.slf4j.LoggerFactory.getLogger(OperationAuditFilter.class)
                    .warn("failed to persist operation audit entry", ex);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ---------------------------------------------------------------- action naming

    private static final Pattern ID = Pattern.compile("^\\d+$");

    /** Derives a stable semantic action from method + URI (path ids collapsed). */
    static String resolveAction(String method, String uri) {
        // /api/v1/users/12/roles → users/{id}/roles
        String path = uri.startsWith("/api/v1/") ? uri.substring(8) : uri.replace("/api/auth/", "auth/");
        String[] seg = path.split("/");
        StringBuilder shaped = new StringBuilder();
        for (String s : seg) {
            if (s.isEmpty()) {
                continue;
            }
            shaped.append(ID.matcher(s).matches() ? "{id}" : s).append('/');
        }
        String template = shaped.length() > 0 ? shaped.substring(0, shaped.length() - 1) : path;
        String verb = switch (method) {
            case "POST" -> "CREATE";
            case "PUT" -> "UPDATE";
            case "PATCH" -> "PATCH";
            case "DELETE" -> "DELETE";
            default -> method;
        };
        // curated names for the non-obvious endpoints
        String named = switch (verb + " " + template) {
            case "PATCH users/{id}/status" -> "USER_STATUS";
            case "PUT users/{id}/roles" -> "USER_ASSIGN_ROLES";
            case "POST users/{id}/reset-password" -> "USER_RESET_PASSWORD";
            case "PATCH users/{id}/org" -> "USER_MOVE_ORG";
            case "POST users/import" -> "USER_IMPORT";
            case "PATCH apps/{id}/status" -> "APP_STATUS";
            case "PUT apps/{id}/roles" -> "APP_GRANT_ROLES";
            case "POST apps/{id}/secret" -> "APP_SECRET_ROTATE";
            case "POST sync/{id}", "POST sync/wecom", "POST sync/dingtalk" -> "SYNC_TRIGGER";
            case "CREATE auth/password" -> "PASSWORD_CHANGE";
            default -> null;
        };
        if (named != null) {
            return named;
        }
        // generic: USER_CREATE / ROLE_UPDATE / ORG_DELETE / APP_CREATE ...
        String resource = seg.length > 0 ? seg[0].toUpperCase() : "API";
        if (template.contains("users/{id}")) {
            resource = "USER";
        } else if (template.startsWith("roles")) {
            resource = "ROLE";
        } else if (template.startsWith("orgs")) {
            resource = "ORG";
        } else if (template.startsWith("apps")) {
            resource = "APP";
        }
        return verb + " " + resource;
    }
}
