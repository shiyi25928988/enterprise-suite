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

    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public Dingtalk getDingtalk() { return dingtalk; }
    public Wecom getWecom() { return wecom; }
    public Csv getCsv() { return csv; }

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
}
