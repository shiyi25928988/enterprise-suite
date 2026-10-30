package io.github.shiyi.auth.server.app;

import java.util.List;

/**
 * Create / update payload for an application.
 *
 * <p>{@code clientId} may be omitted on create (defaults to {@code appCode}).
 * Comma separated fields are accepted as raw strings so the console can send
 * what the operator typed; they are normalised server side.</p>
 */
public record AppRequest(
        String appCode,
        String appName,
        String clientId,
        String description,
        String icon,
        String homeUrl,
        String clientType,
        String redirectUris,
        String postLogoutRedirectUris,
        List<String> grantTypes,
        String scopes,
        String accessPolicy,
        Integer sortNo,
        Integer status,
        Integer accessTokenTtlMinutes,
        Integer deviceCodeTtlMinutes) {
}
