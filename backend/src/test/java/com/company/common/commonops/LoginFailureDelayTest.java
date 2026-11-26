package com.company.common.commonops;

import com.company.common.commonops.service.LoginFailureDelay;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginFailureDelayTest {
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-01-02T00:00:00Z");
    private final List<Long> waits = new ArrayList<>();
    private final LoginFailureDelay delay = new LoginFailureDelay(clock, waits::add);

    @Test void firstFiveFailuresHaveNoDelayThenIncreaseToOneSecondCap() {
        when(clock.instant()).thenReturn(now);
        for (int attempt = 0; attempt < 5; attempt++) delay.failed("192.0.2.1");
        assertThat(waits).isEmpty();
        for (int attempt = 0; attempt < 6; attempt++) delay.failed("192.0.2.1");
        assertThat(waits).containsExactly(250L, 500L, 750L, 1000L, 1000L, 1000L);
    }

    @Test void failureWindowsAreIndependentForEachRemoteAddress() {
        when(clock.instant()).thenReturn(now);
        for (int attempt = 0; attempt < 6; attempt++) delay.failed("192.0.2.1");
        for (int attempt = 0; attempt < 5; attempt++) delay.failed("192.0.2.2");
        assertThat(waits).containsExactly(250L);
        delay.failed("192.0.2.2");
        assertThat(waits).containsExactly(250L, 250L);
    }

    @Test void timestampsAtTheExactSixtySecondBoundaryAreDiscarded() {
        when(clock.instant()).thenReturn(now);
        for (int attempt = 0; attempt < 5; attempt++) delay.failed("192.0.2.1");
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        delay.failed("192.0.2.1");
        assertThat(waits).isEmpty();
    }

    @Test void timestampsInsideTheRollingWindowRemainCounted() {
        when(clock.instant()).thenReturn(now);
        for (int attempt = 0; attempt < 5; attempt++) delay.failed("192.0.2.1");
        when(clock.instant()).thenReturn(now.plusSeconds(59));
        delay.failed("192.0.2.1");
        assertThat(waits).containsExactly(250L);
    }

    @Test void cappedHistoryStillDropsOnlyExpiredAttempts() {
        when(clock.instant()).thenReturn(now);
        for (int attempt = 0; attempt < 10; attempt++) delay.failed("192.0.2.1");
        when(clock.instant()).thenReturn(now.plusSeconds(30));
        for (int attempt = 0; attempt < 4; attempt++) delay.failed("192.0.2.1");
        waits.clear();
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        delay.failed("192.0.2.1");
        assertThat(waits).isEmpty();
        delay.failed("192.0.2.1");
        assertThat(waits).containsExactly(250L);
    }
}
