package com.company.common.monthcloses.service;

import com.company.common.monthcloses.mapper.MonthCloseMapper;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.mapper.UserMapper;
import java.time.YearMonth;
import org.springframework.stereotype.Service;

@Service
public class MonthClosePeriodService {
    private final UserPreferencePeriodSourceMapper source;
    private final BusinessCalendar calendar;
    private final MonthCloseMapper mapper;
    private final UserMapper management;
    public MonthClosePeriodService(UserPreferencePeriodSourceMapper source, BusinessCalendar calendar,
        MonthCloseMapper mapper, UserMapper management) {
        this.source = source;
        this.calendar = calendar;
        this.mapper = mapper;
        this.management = management;
    }
    public void lock() {
        management.lockManagement();
        try {
            mapper.lockSources();
        } catch (org.springframework.dao.PessimisticLockingFailureException failure) {
            throw CodeFailure.conflict();
        }
    }
    public String db(YearMonth month) { return calendar.toDatabaseMonth(month); }
    public YearMonth base() {
        try {
            var value = source.selectCashBaseYm();
            if (value == null) throw new IllegalArgumentException();
            return calendar.fromDatabaseMonth(value.toBigIntegerExact().toString());
        } catch (RuntimeException failure) {
            throw CodeFailure.invalid("yearMonth", "자금수지 시작 월을 확인해 주세요.");
        }
    }
    public void confirmable(YearMonth month) {
        String latest = mapper.latestClosedMonth();
        MonthCloseRules.confirmable(month, calendar.firstOpenMonth(
            latest == null ? null : calendar.fromDatabaseMonth(latest), base()), calendar.currentMonth());
    }
}
