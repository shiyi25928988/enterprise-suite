package io.github.shiyi.auth.server.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * M5c per-device token policy, both halves of it: what an update does to the
 * stored column ({@code null} keeps, non-positive clears) and how a stored
 * column becomes a protocol lifetime (blank/non-positive → platform default).
 * The pair is what makes a custom TTL reversible — without the clear path an
 * admin could set 90 minutes and never get back to 30.
 */
class TokenTtlTest {

    @Test
    void updateKeepsTheCurrentValueWhenTheFieldIsOmitted() {
        assertThat(AppRegistryService.ttlOrKeep(null, 45)).isEqualTo(45);
        assertThat(AppRegistryService.ttlOrKeep(null, null)).isNull();
    }

    @Test
    void updateStoresAPositiveValue() {
        assertThat(AppRegistryService.ttlOrKeep(60, 45)).isEqualTo(60);
        assertThat(AppRegistryService.ttlOrKeep(1, 45)).isEqualTo(1);
    }

    @Test
    void updateClearsTheColumnForZeroOrNegative() {
        assertThat(AppRegistryService.ttlOrKeep(0, 45)).isNull();
        assertThat(AppRegistryService.ttlOrKeep(-5, 45)).isNull();
    }

    @Test
    void accessTokenFallsBackToThirtyMinutes() {
        assertThat(AppRegistryService.accessTtl(null)).isEqualTo(Duration.ofMinutes(30));
        assertThat(AppRegistryService.accessTtl(0)).isEqualTo(Duration.ofMinutes(30));
        assertThat(AppRegistryService.accessTtl(-1)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void configuredLifetimesAreUsedAsIs() {
        assertThat(AppRegistryService.accessTtl(90)).isEqualTo(Duration.ofMinutes(90));
        assertThat(AppRegistryService.deviceTtl(25)).isEqualTo(Duration.ofMinutes(25));
    }

    @Test
    void deviceCodeFallsBackToTenMinutes() {
        assertThat(AppRegistryService.deviceTtl(null)).isEqualTo(Duration.ofMinutes(10));
        assertThat(AppRegistryService.deviceTtl(0)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void clearingACustomPolicyRestoresTheDefaultEndToEnd() {
        // admin sets 90, then resets the field: update clears, build reads the default
        Integer stored = AppRegistryService.ttlOrKeep(90, null);
        assertThat(AppRegistryService.accessTtl(stored)).isEqualTo(Duration.ofMinutes(90));

        stored = AppRegistryService.ttlOrKeep(0, stored);
        assertThat(stored).isNull();
        assertThat(AppRegistryService.accessTtl(stored)).isEqualTo(Duration.ofMinutes(30));
    }
}
