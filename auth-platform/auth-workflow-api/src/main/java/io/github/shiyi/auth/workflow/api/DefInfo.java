package io.github.shiyi.auth.workflow.api;

/** A deployed process definition, as shown in the admin definition list. */
public record DefInfo(String id, String key, String name, int version,
                      boolean suspended, String deploymentId) {
}
