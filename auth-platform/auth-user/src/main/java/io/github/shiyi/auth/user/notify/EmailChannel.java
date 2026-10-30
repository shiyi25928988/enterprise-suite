package io.github.shiyi.auth.user.notify;

import java.util.Date;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * M10b: email contact point. Plain-text mail over SMTP (starttls / implicit
 * TLS both supported) — the payload is the same {@code event} + key/value set
 * the webhook channel posts as JSON, rendered one line per field so a human
 * on call can read it without a JSON viewer.
 *
 * <p>One {@link Session} is built from {@code auth.notify.mail.*} at startup
 * and reused; every {@code deliver} opens its own SMTP connection, so a slow
 * or dead relay costs at most the configured timeouts and never leaves a
 * half-open connection behind for the next event.</p>
 */
public class EmailChannel implements NotifyChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final NotifyProperties.Mail mail;
    private final Session session;

    public EmailChannel(NotifyProperties.Mail mail) {
        this.mail = mail;
        this.session = Session.getInstance(smtpProperties(mail));
    }

    @Override
    public String type() {
        return "email";
    }

    @Override
    public boolean ready(NotifyProperties.Contact contact) {
        return mail.getHost() != null && !mail.getHost().isBlank()
                && contact.getTo() != null && !contact.getTo().isBlank();
    }

    @Override
    public void deliver(NotifyProperties.Contact contact, String event, Map<String, Object> body)
            throws Exception {
        Address[] to = InternetAddress.parse(contact.getTo());
        if (to.length == 0) {
            log.warn("notify email contact has no valid recipient: {}", contact.getTo());
            return;
        }
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(mail.getFrom()));
        msg.setRecipients(Message.RecipientType.TO, to);
        msg.setSubject("[auth-platform] " + event, "UTF-8");
        msg.setText(render(body), "UTF-8");
        msg.setSentDate(new Date());

        String user = mail.getUsername();
        if (user != null && !user.isBlank()) {
            Transport.send(msg, user, mail.getPassword());
        } else {
            Transport.send(msg);
        }
        log.debug("notify email sent for event {} to {}", event, contact.getTo());
    }

    /** Event body as readable lines; the ordering of {@link java.util.LinkedHashMap} is preserved. */
    static String render(Map<String, Object> body) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : body.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    private static Properties smtpProperties(NotifyProperties.Mail mail) {
        Properties p = new Properties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.host", mail.getHost() == null ? "localhost" : mail.getHost());
        p.put("mail.smtp.port", String.valueOf(mail.getPort()));
        p.put("mail.smtp.connectiontimeout", String.valueOf(mail.getTimeoutMs()));
        p.put("mail.smtp.timeout", String.valueOf(mail.getTimeoutMs()));
        p.put("mail.smtp.writetimeout", String.valueOf(mail.getTimeoutMs()));
        if (mail.getUsername() != null && !mail.getUsername().isBlank()) {
            p.put("mail.smtp.auth", "true");
        }
        if (mail.isStartTls()) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        if (mail.isSsl()) {
            p.put("mail.smtp.ssl.enable", "true");
        }
        return p;
    }
}
