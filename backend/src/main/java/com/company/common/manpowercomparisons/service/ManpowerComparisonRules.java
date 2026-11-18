package com.company.common.manpowercomparisons.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.manpowercomparisons.dto.ManpowerComparisonContracts.Row;
import com.company.common.manpowercomparisons.mapper.ManpowerComparisonRows.Source;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class ManpowerComparisonRules {
    private final CalculationRuleService definitions;

    public ManpowerComparisonRules(CalculationRuleService definitions) {
        this.definitions = definitions;
    }

    public void requireDefinitions() {
        var common = definitions.definition("식-00");
        var month = definitions.definition("식-01");
        var capacity = definitions.definition("식-62"); // Capacity thresholds are not UI-11 equality rules.
        var annual = definitions.definition("식-67");
        String formula = comparisonText(annual.formula());
        String details = comparisonText(annual.details());
        if (comparisonText(common.formula()).isBlank() || comparisonText(common.details()).isBlank()
            || comparisonText(capacity.formula()).isBlank() || comparisonText(capacity.details()).isBlank()
            || !comparisonText(month.formula()).contains("소수둘째자리반올림")
            || !formula.contains("차이=실제·예정MM-계획MM차이<0이면부족>0이면초과=0이면맞음")
            || !details.contains("그해몫=계획MM*(그해에걸친프로젝트개월수/전체프로젝트개월수)")
            || !details.contains("소수둘째자리반올림")) {
            throw new IllegalStateException("지원하지 않는 인원 계획 비교 계산식입니다.");
        }
    }

    private static String comparisonText(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\p{javaWhitespace}\\p{Zs}]+", "")
            .replace('−', '-').replace('×', '*').replace('÷', '/')
            .replaceAll("[.,;:。`]+", "")
            .replace("인경우", "이면")
            .replace("일때", "이면")
            .replace("소수둘째자리로", "소수둘째자리");
    }

    public Row compare(Source source, int year) {
        LocalDate start = source.startDate(), end = source.endDate();
        if (end.isBefore(start)) throw new IllegalStateException("프로젝트 수행 기간을 확인해 주세요.");
        LocalDate first = LocalDate.of(year,1,1), last = LocalDate.of(year,12,31);
        if (start.isAfter(last) || end.isBefore(first)) {
            throw new IllegalArgumentException("조회 연도와 겹치는 프로젝트가 아닙니다.");
        }
        // D3: provisional inclusive touched-calendar-month convention, not day fractions.
        long totalMonths = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end)) + 1;
        long yearMonths = ChronoUnit.MONTHS.between(
            YearMonth.from(start.isBefore(first) ? first : start),
            YearMonth.from(end.isAfter(last) ? last : end)) + 1;
        BigDecimal planned = source.plannedMm().multiply(BigDecimal.valueOf(yearMonths))
            .divide(BigDecimal.valueOf(totalMonths), 2, RoundingMode.HALF_UP);
        BigDecimal difference = source.actualMm().subtract(planned);
        String status = difference.signum() < 0 ? "부족" : difference.signum() > 0 ? "초과" : "맞음";
        return new Row(source.projectId(), source.projectName(), source.jobCode(), source.jobName(),
            planned, source.actualMm(), difference, status);
    }
}
