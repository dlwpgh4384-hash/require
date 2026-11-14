package com.company.common.support.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BusinessCalendarTest {
    @Test void todayUsesKoreanDayAcrossUtcMidnight() {
        var calendar = at("2026-11-14T15:00:00Z");
        assertThat(calendar.today()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(calendar.now()).isEqualTo(Instant.parse("2026-11-14T15:00:00Z"));
    }
    @Test void currentMonthUsesKoreanMonthAcrossYearBoundary() {
        assertThat(at("2026-12-31T15:00:00Z").currentMonth()).isEqualTo(YearMonth.of(2027, 1));
        assertThat(at("2026-12-31T14:59:59Z").currentMonth()).isEqualTo(YearMonth.of(2026, 12));
    }
    @Test void firstOpenMonthFollowsLastCloseAcrossYearBoundary() {
        assertThat(at("2026-12-31T15:00:00Z").firstOpenMonth(YearMonth.of(2026, 12), YearMonth.of(2026, 1)))
            .isEqualTo(YearMonth.of(2027, 1));
    }
    @Test void noCloseUsesDatabaseCashBaseNotToday() {
        assertThat(at("2026-12-31T15:00:00Z").firstOpenMonth(null, YearMonth.of(2026, 1)))
            .isEqualTo(YearMonth.of(2026, 1));
    }
    @Test void cashBaseCannotBeBypassedByOlderClose() {
        assertThat(at("2026-01-01T00:00:00Z").firstOpenMonth(YearMonth.of(2025, 1), YearMonth.of(2026, 1)))
            .isEqualTo(YearMonth.of(2026, 1));
    }
    @Test void databaseMonthRoundTripsWithoutDayOrTimezone() {
        var calendar = at("2026-01-01T00:00:00Z");
        assertThat(calendar.toDatabaseMonth(YearMonth.of(2026, 2))).isEqualTo("202602");
        assertThat(calendar.fromDatabaseMonth("202602")).isEqualTo(YearMonth.of(2026, 2));
    }
    @Test void malformedMonthIsRejected() {
        var calendar = at("2026-01-01T00:00:00Z");
        assertThatThrownBy(() -> calendar.fromDatabaseMonth("2026-02")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calendar.fromDatabaseMonth("202613")).isInstanceOf(java.time.DateTimeException.class);
        assertThatThrownBy(() -> calendar.fromDatabaseMonth("000001")).isInstanceOf(IllegalArgumentException.class);
    }
    private BusinessCalendar at(String instant) {
        return new BusinessCalendar(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
