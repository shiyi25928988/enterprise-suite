package io.github.shiyi.auth.user.sync;

import java.time.Instant;
import java.util.List;

/** Outcome of one sync run, also exposed by GET /api/v1/sync. */
public record SyncResult(String source, int orgsCreated, int orgsUpdated,
                         int usersCreated, int usersUpdated,
                         List<String> warnings, Instant finishedAt) {

    public SyncResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
