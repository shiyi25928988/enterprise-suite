package io.github.shiyi.auth.user.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * M10b: LDAP / Active Directory as a directory source, through the same
 * {@link DirectoryProvider} SPI as WeCom, DingTalk and the HR CSV — the
 * platform upsert in {@link UserSyncService} is untouched, so an LDAP-synced
 * user behaves exactly like any other (local shadow account, no usable
 * password, roles assigned in the console).
 *
 * <p>Mapping decisions:</p>
 * <ul>
 *   <li><b>external id = the entry's DN</b> (lower-cased). DNs are the only
 *       stable, unique key an LDAP tree offers — {@code cn} and even
 *       {@code sAMAccountName} can be renamed, and DNs are what the tree
 *       hierarchy is expressed in.</li>
 *   <li><b>org = a user's direct parent entry</b>, when that parent was itself
 *       imported as an org. Users sitting straight under the search root stay
 *       orgless (the platform tree has a single root and no synthetic node is
 *       invented here).</li>
 *   <li><b>org name = {@code ou} → {@code description} → first RDN value</b>,
 *       so both AD ({@code OU=Sales}) and bare {@code description}-only trees
 *       render a readable label.</li>
 *   <li><b>paged results</b> are requested (non-critical) because AD caps an
 *       unpaged search at 1000 entries; servers that do not support the
 *       control simply answer everything in one page and the loop ends.</li>
 * </ul>
 *
 * <p>{@link #fetch()} opens and closes its own connection — no pooling, no
 * cached state, per the SPI contract.</p>
 */
public class LdapDirectory implements DirectoryProvider {

    private static final Logger log = LoggerFactory.getLogger(LdapDirectory.class);

    /** AD's MaxPageSize default; small enough to stay under any server cap. */
    private static final int PAGE_SIZE = 500;

    private final SyncProperties.Ldap props;

    public LdapDirectory(SyncProperties.Ldap props) {
        this.props = props;
    }

    @Override
    public String source() {
        return "ldap";
    }

    @Override
    public DirectorySnapshot fetch() throws Exception {
        String base = required(props.getBaseDn(), "auth.sync.ldap.base-dn");
        String orgBase = blankTo(props.getOrgBaseDn(), base);
        String userBase = blankTo(props.getUserBaseDn(), base);
        required(props.getUrl(), "auth.sync.ldap.url");

        LdapContext ctx = connect();
        try {
            Map<String, ExternalOrg> orgs = readOrgs(ctx, orgBase);
            List<ExternalUser> users = readUsers(ctx, userBase, orgs.keySet());
            log.info("ldap directory: {} orgs / {} users under {} ({} users under {})",
                    orgs.size(), users.size(), base, users.size(), userBase);
            return new DirectorySnapshot(new ArrayList<>(orgs.values()), users);
        } finally {
            close(ctx);
        }
    }

    // ---- connection ----

    private LdapContext connect() throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, props.getUrl());
        env.put(Context.REFERRAL, "ignore");
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(props.getTimeoutMs()));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(props.getTimeoutMs()));
        String bindDn = props.getBindDn();
        if (bindDn != null && !bindDn.isBlank()) {
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, bindDn);
            env.put(Context.SECURITY_CREDENTIALS,
                    props.getBindPassword() == null ? "" : props.getBindPassword());
        }
        // InitialDirContext (not InitialLdapContext) would drop the paged-results control
        return new InitialLdapContext(env, null);
    }

    private static void close(LdapContext ctx) {
        try {
            ctx.close();
        } catch (NamingException e) {
            log.debug("ldap close failed: {}", e.toString());
        }
    }

    // ---- orgs ----

    /**
     * Reads every org below {@code orgBase}. Parents are resolved in a second
     * pass over the collected DNs, so a single search covers the whole subtree
     * regardless of the order the server returns entries in.
     */
    private Map<String, ExternalOrg> readOrgs(LdapContext ctx, String orgBase)
            throws NamingException, IOException {
        String nameAttr = blankTo(props.getOrgNameAttribute(), "ou");
        Map<String, String> names = new LinkedHashMap<>(); // dn -> label
        for (SearchResult sr : search(ctx, orgBase, blankTo(props.getOrgFilter(),
                "(objectClass=organizationalUnit)"), nameAttr, "description")) {
            String dn = dnOf(sr, orgBase);
            if (dn == null) {
                continue;
            }
            String label = first(attr(sr, nameAttr), attr(sr, "description"), firstRdnValue(dn), dn);
            names.putIfAbsent(dn, label);
        }

        Map<String, ExternalOrg> orgs = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : names.entrySet()) {
            String parent = parentDn(e.getKey());
            // an ancestor outside the search scope is not importable — treat as root
            String parentId = parent != null && names.containsKey(parent) ? parent : null;
            orgs.put(e.getKey(), new ExternalOrg(e.getKey(), e.getValue(), parentId, null));
        }
        return orgs;
    }

    // ---- users ----

    private List<ExternalUser> readUsers(LdapContext ctx, String userBase, Set<String> orgDns)
            throws NamingException, IOException {
        String loginAttr = blankTo(props.getLoginAttribute(), "sAMAccountName");
        String nameAttr = blankTo(props.getNameAttribute(), "cn");
        String mobileAttr = blankTo(props.getMobileAttribute(), "mobile");
        String employeeAttr = blankTo(props.getEmployeeNoAttribute(), "employeeID");

        List<ExternalUser> users = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SearchResult sr : search(ctx, userBase, blankTo(props.getUserFilter(),
                "(&(objectClass=person)(!(objectClass=computer)))"),
                loginAttr, nameAttr, mobileAttr, employeeAttr)) {
            String dn = dnOf(sr, userBase);
            if (dn == null) {
                continue;
            }
            String login = first(attr(sr, loginAttr), attr(sr, "uid"), firstRdnValue(dn));
            if (login == null) {
                log.debug("ldap entry {} has no {}, skipped", dn, loginAttr);
                continue;
            }
            if (!seen.add(login.toLowerCase(Locale.ROOT))) {
                log.warn("ldap entry {} skipped: duplicate login name '{}'", dn, login);
                continue;
            }
            String parent = parentDn(dn);
            String orgId = parent != null && orgDns.contains(parent) ? parent : null;
            users.add(new ExternalUser(login, first(attr(sr, nameAttr), login),
                    attr(sr, mobileAttr), attr(sr, employeeAttr), orgId));
        }
        return users;
    }

    // ---- search plumbing (paged) ----

    private List<SearchResult> search(LdapContext ctx, String baseDn, String filter,
                                      String... attrs) throws NamingException, IOException {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(attrs);
        controls.setCountLimit(0);
        controls.setTimeLimit(props.getTimeoutMs());

        List<SearchResult> out = new ArrayList<>();
        byte[] cookie = null;
        ctx.setRequestControls(new Control[]{
                new PagedResultsControl(PAGE_SIZE, Control.NONCRITICAL)});
        try {
            do {
                NamingEnumeration<SearchResult> page = ctx.search(baseDn, filter, controls);
                try {
                    while (page.hasMore()) {
                        out.add(page.next());
                    }
                } finally {
                    page.close();
                }
                cookie = nextCookie(ctx.getResponseControls());
                if (cookie != null && cookie.length > 0) {
                    ctx.setRequestControls(new Control[]{
                            new PagedResultsControl(PAGE_SIZE, cookie, Control.NONCRITICAL)});
                }
            } while (cookie != null && cookie.length > 0);
        } finally {
            ctx.setRequestControls(null);
        }
        return out;
    }

    /** @return the server's paging cookie, or null when it did not page at all */
    private static byte[] nextCookie(Control[] responseControls) {
        if (responseControls == null) {
            return null;
        }
        for (Control c : responseControls) {
            if (c instanceof PagedResultsResponseControl prc) {
                return prc.getCookie();
            }
        }
        return null;
    }

    // ---- DN helpers ----

    /** Full DN of a search hit; JNDI returns it relative to the search base. */
    private static String dnOf(SearchResult sr, String baseDn) {
        String name = sr.getName();
        String dn;
        if (name == null || name.isEmpty()) {
            dn = baseDn;
        } else if (sr.isRelative()) {
            dn = name + "," + baseDn;
        } else {
            dn = name;
        }
        return normalizeDn(dn);
    }

    /** Lower-cased, trimmed, spaces after separators collapsed — the match key. */
    static String normalizeDn(String dn) {
        if (dn == null) {
            return null;
        }
        String[] parts = dn.split(",");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String p = part.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(p);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Parent DN, or null for a root entry. Commas escaped as {@code \,} (legal
     * in a CN, e.g. {@code CN=Smith\, John}) are not separators.
     */
    static String parentDn(String dn) {
        if (dn == null) {
            return null;
        }
        for (int i = 0; i < dn.length(); i++) {
            char c = dn.charAt(i);
            if (c == '\\') {
                i++; // skip the escaped character
            } else if (c == ',') {
                String parent = normalizeDn(dn.substring(i + 1));
                return parent == null || parent.isEmpty() ? null : parent;
            }
        }
        return null;
    }

    private static String firstRdnValue(String dn) {
        if (dn == null) {
            return null;
        }
        int eq = dn.indexOf('=');
        if (eq < 0) {
            return dn;
        }
        int end = dn.length();
        for (int i = eq + 1; i < dn.length(); i++) {
            char c = dn.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == ',') {
                end = i;
                break;
            }
        }
        String value = dn.substring(eq + 1, end).strip();
        return value.isEmpty() ? null : value;
    }

    // ---- attribute / string helpers ----

    private static String attr(SearchResult sr, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Attributes attrs = sr.getAttributes();
        if (attrs == null) {
            return null;
        }
        try {
            Attribute a = attrs.get(id);
            if (a == null || a.size() == 0) {
                return null;
            }
            Object v = a.get();
            String s = v == null ? null : v.toString().strip();
            return s == null || s.isEmpty() ? null : s;
        } catch (NamingException e) {
            return null;
        }
    }

    private static String first(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c.strip();
            }
        }
        return null;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String required(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required when auth.sync.ldap.enabled=true");
        }
        return value.trim();
    }
}
