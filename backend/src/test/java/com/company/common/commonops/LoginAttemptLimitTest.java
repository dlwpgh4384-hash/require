package com.company.common.commonops;

import com.company.common.commonops.service.LoginAttemptLimit;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginAttemptLimitTest {
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-01-02T00:00:00Z");
    private final LoginAttemptLimit limit = new LoginAttemptLimit(clock);

    @Test void twentyFailuresInWindowBlockTheSameAddressOnly() {
        when(clock.instant()).thenReturn(now);
        for (int attempt = 0; attempt < 19; attempt++) limit.failed("192.0.2.1");
        assertThat(limit.blocked("192.0.2.1")).isFalse();
        limit.failed("192.0.2.1");
        assertThat(limit.blocked("192.0.2.1")).isTrue();
        assertThat(limit.blocked("192.0.2.2")).isFalse();
    }

    @Test void sixFailuresDoNotBlockSoTheLockMessageStaysVisible() {
        when(clock.instant()).thenReturn(now);
        for (int attempt = 0; attempt < 6; attempt++) limit.failed("192.0.2.1");
        assertThat(limit.blocked("192.0.2.1")).isFalse();
    }

    @Test void failuresAtTheExactSixtySecondBoundaryNoLongerCount() {
        when(clock.instant()).thenReturn(now);
        for (int attempt = 0; attempt < 20; attempt++) limit.failed("192.0.2.1");
        when(clock.instant()).thenReturn(now.plusSeconds(59));
        assertThat(limit.blocked("192.0.2.1")).isTrue();
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertThat(limit.blocked("192.0.2.1")).isFalse();
    }

    @Test void successClearsTheCounter() {
        when(clock.instant()).thenReturn(now);
        for (int attempt = 0; attempt < 20; attempt++) limit.failed("192.0.2.1");
        limit.succeeded("192.0.2.1");
        assertThat(limit.blocked("192.0.2.1")).isFalse();
    }

    @Test void rollingWindowUnblocksAsOldFailuresExpire() {
        when(clock.instant()).thenReturn(now);
        for (int attempt = 0; attempt < 19; attempt++) limit.failed("192.0.2.1");
        when(clock.instant()).thenReturn(now.plusSeconds(30));
        limit.failed("192.0.2.1");
        assertThat(limit.blocked("192.0.2.1")).isTrue();
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertThat(limit.blocked("192.0.2.1")).isFalse();
    }
}
