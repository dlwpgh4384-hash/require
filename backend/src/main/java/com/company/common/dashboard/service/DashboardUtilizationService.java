package com.company.common.dashboard.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.dashboard.mapper.DashboardMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import org.springframework.stereotype.Service;

@Service
public class DashboardUtilizationService {
    private final DashboardMapper mapper;
    private final CalculationRuleService rules;
    public DashboardUtilizationService(DashboardMapper mapper, CalculationRuleService rules) {
        this.mapper = mapper;
        this.rules = rules;
    }
    public record Result(BigDecimal rate, BigDecimal target, BigDecimal idle) {}
    public Result read(YearMonth month) {
        rules.definition("식-00");
        rules.definition("식-37");
        var people = mapper.utilization(month.toString().replace("-", ""), month.atDay(1), month.atEndOfMonth());
        var rate = people.totalPeople() == 0 ? null : BigDecimal.valueOf(people.assignedPeople())
            .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(people.totalPeople()), 1, RoundingMode.HALF_UP);
        return new Result(rate, rules.effectiveStandard("TGT_UTIL_RT", month).value(),
            BigDecimal.valueOf(people.totalPeople() - people.assignedPeople()));
    }
}
