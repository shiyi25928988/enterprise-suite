package io.github.shiyi.auth.user.notify;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auth.notify.* — outbound notification contact points. Events published
 * through {@link NotificationService} fan out to every configured contact;
 * delivery failures are logged and swallowed (notifications must never break
 * the business action that triggered them).
 */
@ConfigurationProperties("auth.notify")
public class NotifyProperties {

    private List<Contact> contacts = new ArrayList<>();
    private final Mail mail = new Mail();

    public List<Contact> getContacts() { return contacts; }
    public void setContacts(List<Contact> contacts) { this.contacts = contacts; }
    public Mail getMail() { return mail; }

    public static class Contact {
        /** "webhook" (JSON POST) or "email" (SMTP). */
        private String type = "webhook";
        /** Target URL for webhook contacts. */
        private String url;
        /** Recipient list for email contacts, comma separated. */
        private String to;
        /** true → silently skip this contact. */
        private boolean disabled;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public boolean isDisabled() { return disabled; }
        public void setDisabled(boolean disabled) { this.disabled = disabled; }
    }

    /**
     * SMTP settings shared by every email contact. Unset host leaves the email
     * channel unready, so a misconfigured mail block cannot spam a log or a
     * relay on every login.
     */
    public static class Mail {
        private String host;
        private int port = 25;
        private String username;
        private String password;
        private String from = "no-reply@auth-platform.local";
        private boolean startTls;
        private boolean ssl;
        private int timeoutMs = 5000;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public boolean isStartTls() { return startTls; }
        public void setStartTls(boolean startTls) { this.startTls = startTls; }
        public boolean isSsl() { return ssl; }
        public void setSsl(boolean ssl) { this.ssl = ssl; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }
}
