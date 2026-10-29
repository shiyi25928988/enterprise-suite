package io.github.shiyi.auth.server.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * M6b: business-level authentication metrics, scraped via /actuator/prometheus.
 *
 * <p>Listens to Spring Security authentication events, which are published by
 * the ProviderManager for every login path — JSON password login, TOTP second
 * factor, LDAP, WeCom QR — so one listener covers all of them without touching
 * the filters/providers.
 *
 * <p>Counters (tagged by result and provider type):
 * <pre>
 *   auth_login_total{result="success",type="PasswordAuthenticationProvider"}
 *   auth_login_total{result="failure",type="..."}
 * </pre>
 * Note: {@code AuthenticationSuccessEvent} fires on successful *provider*
 * authentication. For the challenge-based two-step login this means the
 * step-1 (password) completion; the TOTP step is a separate provider and
 * therefore shows up as its own success event — intentional, both hops are
 * interesting to monitor.
 */
@Component
public class AuthMetrics {

    private final MeterRegistry registry;

    public AuthMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        counter("success", providerType(event.getSource())).increment();
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        // provider type may be unavailable on some failure paths — fall back to
        // the exception name so lockouts/timeout patterns remain distinguishable
        String type = event.getAuthentication() != null
                ? providerType(event.getAuthentication())
                : event.getException().getClass().getSimpleName();
        counter("failure", type).increment();
    }

    private Counter counter(String result, String type) {
        return Counter.builder("auth_login_total")
                .description("Authentication attempts by result and provider type")
                .tag("result", result)
                .tag("type", type)
                .register(registry);
    }

    private String providerType(Object authentication) {
        String name = authentication.getClass().getSimpleName();
        if (name.equals("UsernamePasswordAuthenticationToken") || name.isEmpty()) {
            return "password";
        }
        return name;
    }
}
