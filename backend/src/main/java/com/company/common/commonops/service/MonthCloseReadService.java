package com.company.common.commonops.service;

import com.company.common.common.MonthCloseCodes;
import com.company.common.commonops.dto.MonthCloseReadContracts.Badge;
import com.company.common.commonops.dto.MonthCloseReadContracts.History;
import com.company.common.commonops.dto.MonthCloseReadContracts.HistoryItem;
import com.company.common.commonops.mapper.MonthCloseReadMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthCloseReadService {
    private final MonthCloseReadMapper mapper;
    private final BusinessCalendar calendar;
    private final MonthCloseCodes codes;

    public MonthCloseReadService(MonthCloseReadMapper mapper, BusinessCalendar calendar, MonthCloseCodes codes) {
        this.mapper = mapper;
        this.calendar = calendar;
        this.codes = codes;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Badge getCommonMonthCloseBadge() {
        return new Badge(!mapper.isClosed(calendar.toDatabaseMonth(calendar.currentMonth())));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public History getCommonMonthCloseHistory(String yearMonth) {
        String month = yearMonth == null ? null : databaseMonth(yearMonth);
        var items = mapper.selectHistory(month).stream().map(row -> new HistoryItem(
            calendar.fromDatabaseMonth(row.month()).toString(),
            codes.name("ACT", row.actionCode()), row.processor(), row.occurredAt(), row.reason(),
            "CLOSE".equals(row.actionCode()) ? remaining(row.remainingNames()) : List.<String>of()
        )).toList();
        return new History(items);
    }

    private String databaseMonth(String yearMonth) {
        if (!yearMonth.matches("[0-9]{4}-(0[1-9]|1[0-2])") || yearMonth.startsWith("0000")) {
            throw CodeFailure.invalid("yearMonth", "년월은 YYYY-MM 형식으로 입력해 주세요.");
        }
        return calendar.toDatabaseMonth(YearMonth.parse(yearMonth));
    }

    private static List<String> remaining(String names) {
        return names == null ? List.of() : Arrays.stream(names.split(","))
            .map(String::strip).filter(name -> !name.isEmpty()).toList();
    }
}
