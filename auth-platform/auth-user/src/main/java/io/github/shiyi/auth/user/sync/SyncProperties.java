package io.github.shiyi.auth.user.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auth.sync.* — directory sync switches. Everything is off by default: the
 * platform runs standalone until a source is explicitly enabled with its
 * credentials, and enabling one never affects the others (loose coupling).
 */
@ConfigurationProperties("auth.sync")
public class SyncProperties {

    /** Cron for the scheduled full sync; "-" (default) disables scheduling. */
    private String cron = "-";

    private final Dingtalk dingtalk = new Dingtalk();
    private final Wecom wecom = new Wecom();
    private final Csv csv = new Csv();
    private final Ldap ldap = new Ldap();

    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public Dingtalk getDingtalk() { return dingtalk; }
    public Wecom getWecom() { return wecom; }
    public Csv getCsv() { return csv; }
    public Ldap getLdap() { return ldap; }

    public static class Dingtalk {
        private boolean enabled;
        private String appKey;
        private String appSecret;
        /** topapi base (dept/user endpoints) */
        private String baseUrl = "https://oapi.dingtalk.com";
        /** new-style token endpoint lives on a different host */
        private String tokenUrl = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
        /**
         * listsub never returns the root dept (id=1), so it is synthesized;
         * users mounted directly under the root are covered too.
         */
        private String rootName = "根组织";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAppKey() { return appKey; }
        public void setAppKey(String appKey) { this.appKey = appKey; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        public String getRootName() { return rootName; }
        public void setRootName(String rootName) { this.rootName = rootName; }
    }

    public static class Wecom {
        private boolean enabled;
        private String corpId;
        private String corpSecret;
        private String baseUrl = "https://qyapi.weixin.qq.com";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCorpId() { return corpId; }
        public void setCorpId(String corpId) { this.corpId = corpId; }
        public String getCorpSecret() { return corpSecret; }
        public void setCorpSecret(String corpSecret) { this.corpSecret = corpSecret; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    /** auth.sync.csv.* — file-based HR export, see {@link CsvDirectory}. */
    public static class Csv {
        private boolean enabled;
        /** Absolute path of the CSV export; re-read on every sync run. */
        private String path;
        private String charset = "UTF-8";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getCharset() { return charset; }
        public void setCharset(String charset) { this.charset = charset; }
    }

    /**
     * auth.sync.ldap.* — plain LDAP / Active Directory as a directory source
     * (M10b). Attribute names default to the AD spelling; OpenLDAP deployments
     * only need {@code login-attribute=uid} and a matching filter.
     */
    public static class Ldap {
        private boolean enabled;
        /** e.g. ldap://dc01.corp.example.com:389 or ldaps://dc01:636 */
        private String url;
        /** Search root, e.g. DC=corp,DC=example,DC=com. */
        private String baseDn;
        /** Service account for the bind; blank means anonymous. */
        private String bindDn;
        private String bindPassword;
        /** Optional narrower roots; both default to baseDn. */
        private String orgBaseDn;
        private String userBaseDn;
        private String orgFilter = "(objectClass=organizationalUnit)";
        /** Already excludes AD computer accounts, which also carry objectClass=person. */
        private String userFilter = "(&(objectClass=person)(!(objectClass=computer)))";
        /** Mapped to the platform username / external id. */
        private String loginAttribute = "sAMAccountName";
        private String nameAttribute = "cn";
        private String mobileAttribute = "mobile";
        private String employeeNoAttribute = "employeeID";
        /** Org display name; falls back to description, then the DN's first RDN. */
        private String orgNameAttribute = "ou";
        private int timeoutMs = 5000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getBaseDn() { return baseDn; }
        public void setBaseDn(String baseDn) { this.baseDn = baseDn; }
        public String getBindDn() { return bindDn; }
        public void setBindDn(String bindDn) { this.bindDn = bindDn; }
        public String getBindPassword() { return bindPassword; }
        public void setBindPassword(String bindPassword) { this.bindPassword = bindPassword; }
        public String getOrgBaseDn() { return orgBaseDn; }
        public void setOrgBaseDn(String orgBaseDn) { this.orgBaseDn = orgBaseDn; }
        public String getUserBaseDn() { return userBaseDn; }
        public void setUserBaseDn(String userBaseDn) { this.userBaseDn = userBaseDn; }
        public String getOrgFilter() { return orgFilter; }
        public void setOrgFilter(String orgFilter) { this.orgFilter = orgFilter; }
        public String getUserFilter() { return userFilter; }
        public void setUserFilter(String userFilter) { this.userFilter = userFilter; }
        public String getLoginAttribute() { return loginAttribute; }
        public void setLoginAttribute(String loginAttribute) { this.loginAttribute = loginAttribute; }
        public String getNameAttribute() { return nameAttribute; }
        public void setNameAttribute(String nameAttribute) { this.nameAttribute = nameAttribute; }
        public String getMobileAttribute() { return mobileAttribute; }
        public void setMobileAttribute(String mobileAttribute) { this.mobileAttribute = mobileAttribute; }
        public String getEmployeeNoAttribute() { return employeeNoAttribute; }
        public void setEmployeeNoAttribute(String employeeNoAttribute) { this.employeeNoAttribute = employeeNoAttribute; }
        public String getOrgNameAttribute() { return orgNameAttribute; }
        public void setOrgNameAttribute(String orgNameAttribute) { this.orgNameAttribute = orgNameAttribute; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }
}
