package com.company.common.preferences.service;

import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.BusinessCalendar;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Service;

@Service
public class UserPreferencePeriodBasis {
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("uuuuMM");
    private final UserPreferencePeriodSourceMapper mapper;
    private final BusinessCalendar calendar;

    public UserPreferencePeriodBasis(UserPreferencePeriodSourceMapper mapper) { this(mapper, new BusinessCalendar()); }

    public UserPreferencePeriodBasis(UserPreferencePeriodSourceMapper mapper, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.calendar = java.util.Objects.requireNonNull(calendar);
    }

    /** Uses the application's calendar (and its Clock) whenever one is registered. */
    @org.springframework.beans.factory.annotation.Autowired
    public UserPreferencePeriodBasis(UserPreferencePeriodSourceMapper mapper,
        org.springframework.beans.factory.ObjectProvider<BusinessCalendar> calendar) {
        this(mapper, calendar.getIfAvailable(BusinessCalendar::new));
    }

    public Map<String, Object> metadata() {
        BigDecimal base = mapper.selectCashBaseYm();
        List<String> closed = mapper.selectClosedMonths();
        if (base == null) return missing();
        try {
            String cash = base.toBigIntegerExact().toString();
            YearMonth cashMonth = parse(cash);
            YearMonth last = null;
            for (String value : closed) {
                YearMonth month = parse(value);
                if (last == null || month.isAfter(last)) last = month;
            }
            String latest = calendar.firstOpenMonth(last, cashMonth).format(YM);
            return Map.of("periodBasis", new Basis(latest, cash, closed));
        } catch (IllegalArgumentException | java.time.DateTimeException | ArithmeticException failure) {
            return missing();
        }
    }

    private static YearMonth parse(String value) {
        if (!value.matches("[0-9]{6}")) throw new IllegalArgumentException();
        return YearMonth.parse(value, YM);
    }

    private Map<String, Object> missing() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("periodBasis", null);
        String current = mapper.selectCurrentKoreanMonth();
        if (current != null) meta.put("currentYearMonth", current);
        meta.put("periodBasisError", "기간 기준을 확인할 수 없습니다. 운영 기준값을 확인해 주세요.");
        return meta;
    }

    public record Basis(String latestYm, String cashBaseYm, List<String> closedMonths) {
        /**
         * 마지막 마감 달의 다음 달(시작 월 CASH_BASE_YM 전은 마감으로 본다). 둘 다 없으면 current.
         * 대시보드 자금 3칸과 AI 분석 도우미 자금 예측이 같이 쓴다.
         */
        public YearMonth firstOpen(YearMonth current) {
            String last = closedMonths == null ? null : closedMonths.stream().filter(m -> m != null && m.matches("[0-9]{6}"))
                .max(String::compareTo).orElse(null);
            YearMonth open = last == null ? null : YearMonth.parse(last, YM).plusMonths(1);
            YearMonth base = cashBaseYm != null && cashBaseYm.matches("[0-9]{6}") ? YearMonth.parse(cashBaseYm, YM) : null;
            if (open == null) return base == null ? current : base;
            return base != null && base.isAfter(open) ? base : open;
        }
    }
}
