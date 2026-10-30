package io.github.shiyi.auth.server.app;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-facing view of a registered application.
 *
 * @param roleIds   roles allowed to access the app (only meaningful when
 *                  {@code accessPolicy = ROLES})
 * @param roleNames display names for {@code roleIds}, in the same order
 * @param accessTokenTtlMinutes M5c per-device access token TTL; null = default 30
 * @param deviceCodeTtlMinutes M5c per-device device-code TTL; null = default 10
 */
public record AppDTO(
        Long id,
        String appCode,
        String appName,
        String clientId,
        String description,
        String icon,
        String homeUrl,
        String clientType,
        String redirectUris,
        String postLogoutRedirectUris,
        String grantTypes,
        String scopes,
        String accessPolicy,
        Integer sortNo,
        Integer status,
        Integer accessTokenTtlMinutes,
        Integer deviceCodeTtlMinutes,
        List<Long> roleIds,
        List<String> roleNames,
        LocalDateTime createTime) {
}
