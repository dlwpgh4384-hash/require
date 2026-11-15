package com.company.common.developers.service;

import com.company.common.calculation.service.CalculationRuleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Restricted adapters for the four developer-list definitions; never evaluates executable text. */
@Component
public class DeveloperManMonthRules {
    private final CalculationRuleService rules;
    public DeveloperManMonthRules(CalculationRuleService rules) { this.rules=rules; }
    public BigDecimal prorate(BigDecimal dailySum,int calendarDays) {
        rules.definition("식-00"); String formula=rules.definition("식-01").formula();
        if (!formula.contains("투입일수") || !formula.contains("달력일수") || !formula.contains("반올림"))
            throw new IllegalStateException("지원하지 않는 식-01 정의입니다.");
        int scale=formula.contains("둘째")?2:formula.contains("셋째")?3:formula.contains("첫째")?1:-1;
        if (scale<0) throw new IllegalStateException("식-01 반올림 자릿수를 확인해 주세요.");
        return dailySum.divide(BigDecimal.valueOf(calendarDays),scale,RoundingMode.HALF_UP);
    }
    public boolean over(BigDecimal total) {
        String formula=rules.definition("식-95").formula();
        var matcher=Pattern.compile("합\\s*>\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(formula);
        if (!matcher.find()) throw new IllegalStateException("식-95 경고 기준을 확인해 주세요.");
        return total.compareTo(new BigDecimal(matcher.group(1)))>0;
    }
    public boolean withdrawalSoon(LocalDate end,LocalDate today) {
        String formula=rules.definition("식-77").formula();
        var matcher=Pattern.compile("이번 달\\s*\\+\\s*([0-9]{1,2})개월").matcher(formula);
        if (!matcher.find()) throw new IllegalStateException("식-77 기간을 확인해 주세요.");
        return !end.isBefore(today) && !end.isAfter(YearMonth.from(today).plusMonths(Integer.parseInt(matcher.group(1))).atEndOfMonth());
    }
}
