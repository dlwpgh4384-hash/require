package com.company.common.profitstructures.service;

import com.company.common.profitstructures.dto.ProfitStructureContracts.Filter;
import com.company.common.support.service.CodeFailure;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public record ProfitStructurePeriod(YearMonth start, YearMonth end, String unit) {
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("uuuuMM");
    public static String key(YearMonth month) { return month.format(YM); }
    public static ProfitStructurePeriod of(Filter filter) {
        if (filter.year() == null || filter.year() < 1 || filter.year() > 9999)
            throw CodeFailure.invalid("year", "연도를 확인해 주세요.");
        if (filter.periodUnit() == null || !List.of("month", "quarter", "year-to-date").contains(filter.periodUnit()))
            throw CodeFailure.invalid("periodUnit", "월, 분기 또는 연 누계를 선택해 주세요.");
        if (filter.baseMonth() == null || !filter.baseMonth().matches("[0-9]{4}(0[1-9]|1[0-2])"))
            throw CodeFailure.invalid("baseMonth", "기준월을 확인해 주세요.");
        YearMonth end = YearMonth.parse(filter.baseMonth(), YM);
        if (end.getYear() != filter.year()) throw CodeFailure.invalid("baseMonth", "선택 연도 안의 기준월을 선택해 주세요.");
        int first = switch (filter.periodUnit()) {
            case "month" -> end.getMonthValue();
            case "quarter" -> (end.getMonthValue() - 1) / 3 * 3 + 1;
            default -> 1;
        };
        return new ProfitStructurePeriod(YearMonth.of(end.getYear(), first), end, filter.periodUnit());
    }
    public ProfitStructurePeriod previous() {
        int length = end.getMonthValue() - start.getMonthValue();
        YearMonth first = start.minusMonths(unit.equals("month") ? 1 : unit.equals("quarter") ? 3 : 12);
        return new ProfitStructurePeriod(first, first.plusMonths(length), unit);
    }
    public ProfitStructurePeriod previousYear() { return new ProfitStructurePeriod(start.minusYears(1), end.minusYears(1), unit); }
    public List<ProfitStructurePeriod> buckets() {
        List<ProfitStructurePeriod> result = new ArrayList<>();
        for (YearMonth first = YearMonth.of(end.getYear(), 1); !first.isAfter(end);) {
            YearMonth last = unit.equals("month") ? first : unit.equals("quarter") ? first.plusMonths(2) : end;
            if (last.isAfter(end)) last = end;
            result.add(new ProfitStructurePeriod(first, last, unit));
            first = last.plusMonths(1);
        }
        return result;
    }
    public String label() { return start.equals(end) ? key(end) : key(start) + "–" + key(end); }
}
