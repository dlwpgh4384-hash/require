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
    private boolean partialQuarter() { return unit.equals("quarter") && end.getMonthValue() - start.getMonthValue() < 2; }
    /** 시안: 월은 전월, 분기는 전 분기(진행 중이면 같은 개월 수), 연 누계는 직전 비교 없음(전년 같은 기간만). */
    public ProfitStructurePeriod previous() {
        if (unit.equals("year-to-date")) return null;
        int length = end.getMonthValue() - start.getMonthValue();
        YearMonth first = start.minusMonths(unit.equals("month") ? 1 : 3);
        return new ProfitStructurePeriod(first, first.plusMonths(length), unit);
    }
    public String previousLabel() {
        return switch (unit) {
            case "month" -> "전월";
            case "quarter" -> partialQuarter() ? "전 분기 같은 기간" : "전 분기";
            default -> null;
        };
    }
    public ProfitStructurePeriod previousYear() { return new ProfitStructurePeriod(start.minusYears(1), end.minusYears(1), unit); }
    /** 숫자로 보기·추이 차트의 기간 칸. 시안: 월별, 분기별(진행 중 분기는 기준월까지), 월별 누계(1월, 1~2월 …). */
    public List<ProfitStructurePeriod> buckets() {
        List<ProfitStructurePeriod> result = new ArrayList<>();
        YearMonth january = YearMonth.of(end.getYear(), 1);
        if (unit.equals("year-to-date")) {
            for (YearMonth last = january; !last.isAfter(end); last = last.plusMonths(1))
                result.add(new ProfitStructurePeriod(january, last, unit));
            return result;
        }
        for (YearMonth first = january; !first.isAfter(end);) {
            YearMonth last = unit.equals("month") ? first : first.plusMonths(2);
            if (last.isAfter(end)) last = end;
            result.add(new ProfitStructurePeriod(first, last, unit));
            first = last.plusMonths(1);
        }
        return result;
    }
    /** 시안 기간 이름: 「3월」, 「2분기」, 「4분기(11월까지)」, 연 누계는 끝 달 「5월」. */
    public String label() {
        if (unit.equals("quarter")) {
            int quarter = (start.getMonthValue() - 1) / 3 + 1;
            return quarter + "분기" + (partialQuarter() ? "(" + end.getMonthValue() + "월까지)" : "");
        }
        return end.getMonthValue() + "월";
    }
    /** 요약 칸·차트 머리의 기간 이름(시안 perName). */
    public String name() {
        return switch (unit) {
            case "month" -> end.getYear() + "-" + String.format("%02d", end.getMonthValue());
            case "quarter" -> end.getYear() + "년 " + label();
            default -> end.getYear() + "년 1~" + end.getMonthValue() + "월 누계";
        };
    }
}
