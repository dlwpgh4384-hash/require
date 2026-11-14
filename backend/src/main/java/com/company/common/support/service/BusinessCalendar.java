package com.company.common.support.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class BusinessCalendar {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DB_MONTH = DateTimeFormatter.ofPattern("uuuuMM");
    private final Clock clock;

    public BusinessCalendar() { this(Clock.systemUTC()); }

    public BusinessCalendar(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    public Instant now() { return clock.instant(); }
    public LocalDate today() { return now().atZone(KOREA).toLocalDate(); }
    public YearMonth currentMonth() { return YearMonth.from(today()); }

    public YearMonth firstOpenMonth(YearMonth closedMonth, YearMonth cashBaseMonth) {
        Objects.requireNonNull(cashBaseMonth);
        YearMonth afterClose = closedMonth == null ? cashBaseMonth : closedMonth.plusMonths(1);
        return afterClose.isBefore(cashBaseMonth) ? cashBaseMonth : afterClose;
    }

    public String toDatabaseMonth(YearMonth month) {
        Objects.requireNonNull(month);
        if (month.getYear() < 1 || month.getYear() > 9999) {
            throw new IllegalArgumentException("적용 월을 확인해 주세요.");
        }
        return month.format(DB_MONTH);
    }

    public YearMonth fromDatabaseMonth(String month) {
        if (month == null || !month.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("적용 월을 확인해 주세요.");
        }
        YearMonth parsed = YearMonth.parse(month, DB_MONTH);
        if (parsed.getYear() < 1) throw new IllegalArgumentException("적용 월을 확인해 주세요.");
        return parsed;
    }
}
