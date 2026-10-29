package io.github.shiyi.auth.user.sync;

/**
 * Loose coupling point for directory sources: each provider (WeCom, DingTalk,
 * later plain LDAP / HR CSV) fetches a {@link DirectorySnapshot}; the platform
 * side ({@link UserSyncService}) does the upsert and knows nothing about the
 * source APIs. Providers are registered as beans and activated purely by
 * configuration — no code change to add a source.
 */
public interface DirectoryProvider {

    /** Identifier stored in sys_user/sys_org.external_source, e.g. "wecom". */
    String source();

    /** Pull the full directory. Implementations must be side-effect free. */
    DirectorySnapshot fetch() throws Exception;
}
