package com.company.common.userpreferences;

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

    public UserPreferencePeriodBasis(UserPreferencePeriodSourceMapper mapper) { this.mapper = mapper; }

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
            String latest = (last == null ? cashMonth : last.plusMonths(1)).format(YM);
            return Map.of("periodBasis", new Basis(latest, cash, closed));
        } catch (IllegalArgumentException | java.time.DateTimeException | ArithmeticException failure) {
            return missing();
        }
    }

    private static YearMonth parse(String value) {
        if (!value.matches("[0-9]{6}")) throw new IllegalArgumentException();
        return YearMonth.parse(value, YM);
    }

    private static Map<String, Object> missing() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("periodBasis", null);
        meta.put("periodBasisError", "기간 기준을 확인할 수 없습니다. 운영 기준값을 확인해 주세요.");
        return meta;
    }

    public record Basis(String latestYm, String cashBaseYm, List<String> closedMonths) {}
}
