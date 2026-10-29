package io.github.shiyi.auth.user.sync;

import java.util.List;

/** A point-in-time view of an external directory, ready to be upserted. */
public record DirectorySnapshot(List<ExternalOrg> orgs, List<ExternalUser> users) {

    public DirectorySnapshot {
        orgs = orgs == null ? List.of() : List.copyOf(orgs);
        users = users == null ? List.of() : List.copyOf(users);
    }
}
