package io.github.shiyi.auth.user.anomaly;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * M5c login anomaly detection rules (auth.anomaly.*). Rule engines like this
 * stay deliberately dumb: two high-signal, low-false-positive rules —
 * unexpected location and credential stuffing — fanned out through the
 * notification contact points. Everything else is visible in the login audit.
 */
@ConfigurationProperties(prefix = "auth.anomaly")
public class AnomalyProperties {

    private boolean enabled = true;

    /** Days of login history scanned for the known-location set. */
    private int locationWindowDays = 30;

    /** Failed logins for one username within {@code bruteWindowMinutes} that count as brute force. */
    private int bruteThreshold = 5;

    private int bruteWindowMinutes = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getLocationWindowDays() { return locationWindowDays; }
    public void setLocationWindowDays(int locationWindowDays) { this.locationWindowDays = locationWindowDays; }

    public int getBruteThreshold() { return bruteThreshold; }
    public void setBruteThreshold(int bruteThreshold) { this.bruteThreshold = bruteThreshold; }

    public int getBruteWindowMinutes() { return bruteWindowMinutes; }
    public void setBruteWindowMinutes(int bruteWindowMinutes) { this.bruteWindowMinutes = bruteWindowMinutes; }
}
