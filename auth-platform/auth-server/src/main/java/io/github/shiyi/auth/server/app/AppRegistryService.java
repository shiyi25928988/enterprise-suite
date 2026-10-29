package io.github.shiyi.auth.server.app;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.transaction.annotation.Transactional;

import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;

/**
 * Application registry: business metadata (sys_app) kept in sync with the
 * protocol client (oauth2_registered_client).
 *
 * <p>Creating an app in the console therefore provisions a working OIDC client
 * in one step; deleting it revokes the client so no token can be minted for it
 * anymore. Access control is role based: a user may only complete the authorize
 * flow for an app when at least one of their roles is granted to that app
 * (or the app is open to all users).</p>
 */
public class AppRegistryService {

    private static final Logger log = LoggerFactory.getLogger(AppRegistryService.class);

    public static final String POLICY_ALL = "ALL";
    public static final String POLICY_ROLES = "ROLES";
    public static final String TYPE_PUBLIC = "PUBLIC";
    public static final String TYPE_CONFIDENTIAL = "CONFIDENTIAL";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;

    public AppRegistryService(JdbcTemplate jdbc, RegisteredClientRepository clients,
                              PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------------------------------------------------------------- queries

    public List<AppDTO> list() {
        List<AppDTO> apps = jdbc.query(
                "SELECT * FROM sys_app WHERE deleted = 0 ORDER BY sort_no, id",
                (rs, i) -> mapRow(rs));
        return withRoles(apps);
    }

    public AppDTO get(Long id) {
        List<AppDTO> found = jdbc.query(
                "SELECT * FROM sys_app WHERE id = ? AND deleted = 0",
                (rs, i) -> mapRow(rs), id);
        if (found.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "application not found: " + id);
        }
        return withRoles(found).get(0);
    }

    /** Applications the user may open — used by the portal. */
    public List<AppDTO> accessibleFor(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<AppDTO> apps = jdbc.query("""
                SELECT a.* FROM sys_app a
                 WHERE a.deleted = 0 AND a.status = 1
                   AND (a.access_policy = 'ALL'
                        OR EXISTS (SELECT 1 FROM sys_app_role ar
                                     JOIN sys_user_role ur ON ur.role_id = ar.role_id
                                    WHERE ar.app_id = a.id AND ur.user_id = ?))
                 ORDER BY a.sort_no, a.id
                """, (rs, i) -> mapRow(rs), userId);
        return apps;
    }

    /**
     * @return {@code null} when access is allowed, otherwise a short machine
     *         readable reason ({@code APP_DISABLED}, {@code NO_ROLE})
     */
    public String denyReason(Long userId, String clientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, status, access_policy FROM sys_app WHERE client_id = ? AND deleted = 0",
                clientId);
        if (rows.isEmpty()) {
            // client registered outside the console (e.g. seeded protocol client):
            // no business policy attached, do not block the protocol flow
            return null;
        }
        Map<String, Object> app = rows.get(0);
        int status = ((Number) app.get("status")).intValue();
        if (status != 1) {
            return "APP_DISABLED";
        }
        if (POLICY_ALL.equals(app.get("access_policy"))) {
            return null;
        }
        Long appId = ((Number) app.get("id")).longValue();
        Integer granted = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_app_role ar
                  JOIN sys_user_role ur ON ur.role_id = ar.role_id
                 WHERE ar.app_id = ? AND ur.user_id = ?
                """, Integer.class, appId, userId);
        return (granted != null && granted > 0) ? null : "NO_ROLE";
    }

    // ---------------------------------------------------------------- mutation

    @Transactional
    public CreatedApp create(AppRequest req) {
        String appCode = require(req.appCode(), "appCode");
        String appName = require(req.appName(), "appName");
        Integer dup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_app WHERE app_code = ? AND deleted = 0", Integer.class, appCode);
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.CONFLICT, "app code already exists: " + appCode);
        }
        String clientId = blankTo(req.clientId(), appCode);
        Integer clientDup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oauth2_registered_client WHERE client_id = ?", Integer.class, clientId);
        if (clientDup != null && clientDup > 0) {
            throw new BizException(ErrorCode.CONFLICT, "client id already exists: " + clientId);
        }

        String clientType = normalizeType(req.clientType());
        List<String> grants = normalizeGrants(req.grantTypes(), clientType);
        List<String> redirects = splitCsv(req.redirectUris());
        List<String> postLogout = splitCsv(req.postLogoutRedirectUris());
        List<String> scopes = splitCsv(blankTo(req.scopes(), "openid,profile"));
        validate(appName, clientType, grants, redirects, scopes);

        String plainSecret = TYPE_CONFIDENTIAL.equals(clientType) ? newSecret() : null;
        RegisteredClient client = buildClient(UUID.randomUUID().toString(), clientId, appName, clientType,
                grants, redirects, postLogout, scopes, null, plainSecret);
        clients.save(client);

        jdbc.update("""
                INSERT INTO sys_app (app_code, app_name, registered_client_id, client_id, description,
                                     icon, home_url, client_type, redirect_uris, post_logout_redirect_uris,
                                     grant_types, scopes, access_policy, sort_no, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                appCode, appName, client.getId(), clientId, req.description(),
                blankTo(req.icon(), appCode.substring(0, 1).toUpperCase()),
                emptyToNull(req.homeUrl()), clientType,
                String.join(",", redirects), String.join(",", postLogout),
                String.join(",", grants), String.join(",", scopes),
                normalizePolicy(req.accessPolicy()), req.sortNo() == null ? 0 : req.sortNo(),
                req.status() == null ? 1 : req.status());

        Long id = jdbc.queryForObject("SELECT id FROM sys_app WHERE client_id = ?", Long.class, clientId);
        log.info("Registered application '{}' (client_id={}, type={}, policy={})",
                appCode, clientId, clientType, normalizePolicy(req.accessPolicy()));
        return new CreatedApp(get(id), plainSecret);
    }

    @Transactional
    public AppDTO update(Long id, AppRequest req) {
        AppDTO current = get(id);
        String appName = blankTo(req.appName(), current.appName());
        String clientType = normalizeType(blankTo(req.clientType(), current.clientType()));
        List<String> grants = normalizeGrants(req.grantTypes(), clientType);
        if (grants.isEmpty()) {
            grants = splitCsv(current.grantTypes());
        }
        List<String> redirects = req.redirectUris() == null
                ? splitCsv(current.redirectUris()) : splitCsv(req.redirectUris());
        List<String> postLogout = req.postLogoutRedirectUris() == null
                ? splitCsv(current.postLogoutRedirectUris()) : splitCsv(req.postLogoutRedirectUris());
        List<String> scopes = req.scopes() == null ? splitCsv(current.scopes()) : splitCsv(req.scopes());
        validate(appName, clientType, grants, redirects, scopes);

        RegisteredClient existing = clients.findByClientId(current.clientId());
        if (existing == null) {
            throw new BizException(ErrorCode.CONFLICT,
                    "protocol client missing for " + current.clientId() + ", recreate the application");
        }
        RegisteredClient rebuilt = buildClient(existing.getId(), current.clientId(), appName, clientType,
                grants, redirects, postLogout, scopes,
                TYPE_CONFIDENTIAL.equals(clientType) ? existing.getClientSecret() : null, null);
        clients.save(rebuilt);

        jdbc.update("""
                UPDATE sys_app
                   SET app_name = ?, description = ?, icon = ?, home_url = ?, client_type = ?,
                       redirect_uris = ?, post_logout_redirect_uris = ?, grant_types = ?, scopes = ?,
                       access_policy = ?, sort_no = ?, status = ?
                 WHERE id = ?
                """,
                appName, req.description() == null ? current.description() : req.description(),
                blankTo(req.icon(), current.icon()),
                req.homeUrl() == null ? current.homeUrl() : emptyToNull(req.homeUrl()),
                clientType, String.join(",", redirects), String.join(",", postLogout),
                String.join(",", grants), String.join(",", scopes),
                req.accessPolicy() == null ? current.accessPolicy() : normalizePolicy(req.accessPolicy()),
                req.sortNo() == null ? current.sortNo() : req.sortNo(),
                req.status() == null ? current.status() : req.status(), id);
        return get(id);
    }

    @Transactional
    public AppDTO setRoles(Long id, List<Long> roleIds) {
        AppDTO app = get(id);
        jdbc.update("DELETE FROM sys_app_role WHERE app_id = ?", id);
        if (roleIds != null) {
            for (Long roleId : new LinkedHashSet<>(roleIds)) {
                if (roleId != null) {
                    jdbc.update("INSERT INTO sys_app_role (app_id, role_id) VALUES (?, ?)", id, roleId);
                }
            }
        }
        // granting specific roles only makes sense with the ROLES policy
        if (!POLICY_ROLES.equals(app.accessPolicy())) {
            jdbc.update("UPDATE sys_app SET access_policy = ? WHERE id = ?", POLICY_ROLES, id);
        }
        return get(id);
    }

    @Transactional
    public AppDTO setStatus(Long id, int status) {
        get(id);
        jdbc.update("UPDATE sys_app SET status = ? WHERE id = ?", status, id);
        return get(id);
    }

    /** Rotates the client secret; the new plaintext value is returned exactly once. */
    @Transactional
    public String rotateSecret(Long id) {
        AppDTO app = get(id);
        if (!TYPE_CONFIDENTIAL.equals(app.clientType())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "public clients (PKCE) have no client secret; switch the client type to CONFIDENTIAL first");
        }
        RegisteredClient existing = clients.findByClientId(app.clientId());
        if (existing == null) {
            throw new BizException(ErrorCode.CONFLICT, "protocol client missing for " + app.clientId());
        }
        String plain = newSecret();
        RegisteredClient rebuilt = buildClient(existing.getId(), existing.getClientId(), existing.getClientName(),
                TYPE_CONFIDENTIAL,
                new ArrayList<>(existing.getAuthorizationGrantTypes().stream()
                        .map(AuthorizationGrantType::getValue).toList()),
                new ArrayList<>(existing.getRedirectUris()),
                new ArrayList<>(existing.getPostLogoutRedirectUris()),
                new ArrayList<>(existing.getScopes()), null, plain);
        clients.save(rebuilt);
        log.info("Rotated client secret for application '{}'", app.appCode());
        return plain;
    }

    /** Soft-deletes the app and hard-deletes the protocol client + consents. */
    @Transactional
    public void delete(Long id) {
        AppDTO app = get(id);
        jdbc.update("UPDATE sys_app SET deleted = 1, status = 0 WHERE id = ?", id);
        jdbc.update("DELETE FROM sys_app_role WHERE app_id = ?", id);
        jdbc.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?",
                app.clientId());
        jdbc.update("DELETE FROM oauth2_registered_client WHERE client_id = ?", app.clientId());
        log.info("Removed application '{}' and its protocol client", app.appCode());
    }

    /**
     * Gives every pre-existing protocol client (e.g. the demo clients seeded at
     * first boot) a registry row, so the console can manage them. Idempotent.
     */
    public void seedMissingClients() {
        List<Map<String, Object>> missing = jdbc.queryForList("""
                SELECT c.client_id, c.client_name
                  FROM oauth2_registered_client c
                 WHERE NOT EXISTS (SELECT 1 FROM sys_app a WHERE a.client_id = c.client_id)
                """);
        for (Map<String, Object> row : missing) {
            String clientId = (String) row.get("client_id");
            String clientName = (String) row.get("client_name");
            jdbc.update("""
                    INSERT INTO sys_app (app_code, app_name, client_id, description, icon,
                                         client_type, access_policy, status)
                    VALUES (?, ?, ?, ?, ?, 'PUBLIC', 'ALL', 1)
                    """, clientId, clientName, clientId,
                    "Existing protocol client, imported automatically", "◆");
            log.info("Imported protocol client '{}' into the application registry", clientId);
        }
    }

    // ---------------------------------------------------------------- helpers

    private List<AppDTO> withRoles(List<AppDTO> apps) {
        if (apps.isEmpty()) {
            return apps;
        }
        Map<Long, List<Long>> roleIds = new HashMap<>();
        Map<Long, List<String>> roleNames = new HashMap<>();
        jdbc.query("""
                SELECT ar.app_id, r.id, r.role_name
                  FROM sys_app_role ar JOIN sys_role r ON r.id = ar.role_id
                 ORDER BY r.id
                """, rs -> {
                    long appId = rs.getLong("app_id");
                    roleIds.computeIfAbsent(appId, k -> new ArrayList<>()).add(rs.getLong("id"));
                    roleNames.computeIfAbsent(appId, k -> new ArrayList<>()).add(rs.getString("role_name"));
                });
        List<AppDTO> out = new ArrayList<>(apps.size());
        for (AppDTO a : apps) {
            out.add(new AppDTO(a.id(), a.appCode(), a.appName(), a.clientId(), a.description(), a.icon(),
                    a.homeUrl(), a.clientType(), a.redirectUris(), a.postLogoutRedirectUris(),
                    a.grantTypes(), a.scopes(), a.accessPolicy(), a.sortNo(), a.status(),
                    roleIds.getOrDefault(a.id(), List.of()),
                    roleNames.getOrDefault(a.id(), List.of()), a.createTime()));
        }
        return out;
    }

    private AppDTO mapRow(ResultSet rs) throws SQLException {
        return new AppDTO(
                rs.getLong("id"),
                rs.getString("app_code"),
                rs.getString("app_name"),
                rs.getString("client_id"),
                rs.getString("description"),
                rs.getString("icon"),
                rs.getString("home_url"),
                rs.getString("client_type"),
                rs.getString("redirect_uris"),
                rs.getString("post_logout_redirect_uris"),
                rs.getString("grant_types"),
                rs.getString("scopes"),
                rs.getString("access_policy"),
                rs.getInt("sort_no"),
                rs.getInt("status"),
                List.of(), List.of(),
                rs.getTimestamp("create_time") == null ? null
                        : rs.getTimestamp("create_time").toLocalDateTime());
    }

    private RegisteredClient buildClient(String id, String clientId, String name, String clientType,
                                         List<String> grants, List<String> redirects,
                                         List<String> postLogout, List<String> scopes,
                                         String existingEncodedSecret, String plainSecret) {
        boolean confidential = TYPE_CONFIDENTIAL.equals(clientType);
        RegisteredClient.Builder b = RegisteredClient.withId(id)
                .clientId(clientId)
                .clientName(name)
                .clientAuthenticationMethod(confidential
                        ? ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                        : ClientAuthenticationMethod.NONE);
        if (confidential) {
            String secret = plainSecret != null
                    ? passwordEncoder.encode(plainSecret) : existingEncodedSecret;
            if (secret != null) {
                b.clientSecret(secret);
            }
        }
        grants.forEach(g -> b.authorizationGrantType(new AuthorizationGrantType(g)));
        redirects.forEach(b::redirectUri);
        postLogout.forEach(b::postLogoutRedirectUri);
        scopes.forEach(b::scope);
        b.clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(true)
                .requireProofKey(!confidential)
                .build());
        b.tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(30))
                .refreshTokenTimeToLive(Duration.ofHours(8))
                .deviceCodeTimeToLive(Duration.ofMinutes(10))
                .reuseRefreshTokens(false)
                .build());
        return b.build();
    }

    private void validate(String appName, String clientType, List<String> grants,
                          List<String> redirects, List<String> scopes) {
        if (!TYPE_PUBLIC.equals(clientType) && !TYPE_CONFIDENTIAL.equals(clientType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "clientType must be PUBLIC or CONFIDENTIAL");
        }
        if (grants.contains(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()) && redirects.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "authorization_code requires at least one redirect URI");
        }
        if (scopes.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "at least one scope is required");
        }
        if (TYPE_PUBLIC.equals(clientType)
                && grants.contains(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "a public (PKCE) client cannot use client_credentials");
        }
    }

    private List<String> normalizeGrants(List<String> requested, String clientType) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (requested != null) {
            for (String g : requested) {
                if (g != null && !g.isBlank()) {
                    out.add(g.trim());
                }
            }
        }
        if (out.isEmpty()) {
            out.add(AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        }
        if (out.contains(AuthorizationGrantType.AUTHORIZATION_CODE.getValue())) {
            out.add(AuthorizationGrantType.REFRESH_TOKEN.getValue());
        }
        out.removeIf(g -> !List.of(
                AuthorizationGrantType.AUTHORIZATION_CODE.getValue(),
                AuthorizationGrantType.REFRESH_TOKEN.getValue(),
                AuthorizationGrantType.DEVICE_CODE.getValue(),
                AuthorizationGrantType.CLIENT_CREDENTIALS.getValue()).contains(g));
        return new ArrayList<>(out);
    }

    private static String normalizeType(String type) {
        return TYPE_CONFIDENTIAL.equalsIgnoreCase(type == null ? "" : type.trim())
                ? TYPE_CONFIDENTIAL : TYPE_PUBLIC;
    }

    private static String normalizePolicy(String policy) {
        return POLICY_ALL.equalsIgnoreCase(policy == null ? "" : policy.trim()) ? POLICY_ALL : POLICY_ROLES;
    }

    private static List<String> splitCsv(String value) {
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        for (String part : value.split("[,\\s]+")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String newSecret() {
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        return "sas_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** Result of {@link #create(AppRequest)}: the client secret is shown only here. */
    public record CreatedApp(AppDTO app, String clientSecret) {
    }
}
