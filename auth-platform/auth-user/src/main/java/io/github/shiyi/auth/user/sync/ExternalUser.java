package io.github.shiyi.auth.user.sync;

import org.springframework.lang.Nullable;

/**
 * A user as seen by an external directory. {@code primaryOrgExternalId} is the
 * first resolvable department of the source account — the platform's sys_user
 * is single-org, so multi-department members keep their primary one.
 */
public record ExternalUser(String externalId, String name,
                           @Nullable String mobile,
                           @Nullable String employeeNo,
                           @Nullable String primaryOrgExternalId) {
}
