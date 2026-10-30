package io.github.shiyi.auth.user.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * DN handling decides the external id and the parent/child binding of every
 * synced entry, so the two helpers that do it are pinned here: normalisation
 * must be case- and whitespace-insensitive (AD returns DNs verbatim), and the
 * parent split must respect RFC 4514 escaped separators — otherwise
 * {@code CN=Smith\, John} silently becomes its own org root.
 */
class LdapDirectoryTest {

    @Test
    void normaliseLowercasesTrimsAndDropsEmptySegments() {
        assertThat(LdapDirectory.normalizeDn("CN=Zhang San, OU=RND , DC=corp,DC=example,DC=com"))
                .isEqualTo("cn=zhang san,ou=rnd,dc=corp,dc=example,dc=com");
        assertThat(LdapDirectory.normalizeDn("cn=a,,dc=b")).isEqualTo("cn=a,dc=b");
        assertThat(LdapDirectory.normalizeDn(null)).isNull();
    }

    @Test
    void parentIsTheFirstUnescapedSeparatorAndEverythingAfterIt() {
        assertThat(LdapDirectory.parentDn("cn=zhang.san,ou=rnd,dc=corp,dc=example,dc=com"))
                .isEqualTo("ou=rnd,dc=corp,dc=example,dc=com");
        // two-level DN: only the first separator splits, the rest stays with the parent
        assertThat(LdapDirectory.parentDn("OU=RND,DC=corp"))
                .isEqualTo("dc=corp");
    }

    @Test
    void rootEntryHasNoParent() {
        assertThat(LdapDirectory.parentDn("dc=corp,dc=example,dc=com"))
                .isEqualTo("dc=example,dc=com");
        assertThat(LdapDirectory.parentDn("dc=com")).isNull();
        assertThat(LdapDirectory.parentDn("")).isNull();
        assertThat(LdapDirectory.parentDn(null)).isNull();
    }

    @Test
    void escapedCommaStaysInsideTheRdn() {
        // CN=Smith\, John,OU=Staff,... — the first real separator is after "John"
        assertThat(LdapDirectory.parentDn("CN=Smith\\, John,OU=Staff,DC=corp"))
                .isEqualTo("ou=staff,dc=corp");
    }

    @Test
    void trailingSeparatorYieldsNoParent() {
        assertThat(LdapDirectory.parentDn("cn=orphan,")).isNull();
    }
}
