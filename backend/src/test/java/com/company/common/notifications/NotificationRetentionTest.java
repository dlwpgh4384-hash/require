package com.company.common.notifications;

import com.company.common.notifications.mapper.NotificationMapper;
import com.company.common.notifications.service.NotificationRetention;
import com.company.common.support.service.BusinessCalendar;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationRetentionTest {
    @Test void removesOnlyBeforeKoreanCalendarCutoffAndPreservesBoundary() {
        var now = Instant.parse("2026-03-31T16:00:00Z");
        var cutoff = Instant.parse("2026-03-01T16:00:00Z");
        var before = cutoff.minusNanos(1);
        var after = cutoff.plusNanos(1);
        var rows = new ArrayList<>(List.of(before, cutoff, after));
        var mapper = mock(NotificationMapper.class);
        when(mapper.deleteExpired(any())).thenAnswer(call -> {
            Instant boundary = call.getArgument(0);
            int count = rows.size();
            rows.removeIf(row -> row.isBefore(boundary));
            return count - rows.size();
        });
        var retention = new NotificationRetention(mapper, new BusinessCalendar(Clock.fixed(now, ZoneOffset.UTC)));
        assertThat(retention.purgeExpired()).isEqualTo(1);
        verify(mapper).deleteExpired(cutoff);
        assertThat(rows).containsExactly(cutoff, after);
    }
}
