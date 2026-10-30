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

    public List<Contact> getContacts() { return contacts; }
    public void setContacts(List<Contact> contacts) { this.contacts = contacts; }

    public static class Contact {
        /** "webhook" (JSON POST); more types may follow (email, IM bots). */
        private String type = "webhook";
        /** Target URL for webhook contacts. */
        private String url;
        /** true → silently skip this contact. */
        private boolean disabled;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public boolean isDisabled() { return disabled; }
        public void setDisabled(boolean disabled) { this.disabled = disabled; }
    }
}
