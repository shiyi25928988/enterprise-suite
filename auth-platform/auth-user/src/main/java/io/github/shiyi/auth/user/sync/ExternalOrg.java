package io.github.shiyi.auth.user.sync;

import java.util.List;

/**
 * An org/department node as seen by an external directory (WeCom / DingTalk).
 * The parent is referenced by the <em>external</em> id; roots use a null parent.
 */
public record ExternalOrg(String externalId, String name, String parentExternalId, Integer sortNo) {
}
