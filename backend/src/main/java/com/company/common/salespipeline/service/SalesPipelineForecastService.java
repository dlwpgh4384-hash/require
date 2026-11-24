package com.company.common.salespipeline.service;

import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.mapper.SalesPipelineRows.MonthQuery;
import com.company.common.support.service.BusinessCalendar;

import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.MonthFilter;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import com.company.common.withdrawals.service.WithdrawalCalculationRules.*;
import java.math.BigDecimal;

import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SalesPipelineForecastService {
    private final SalesPipelineMapper mapper;
    private final WithdrawalMapper workforce;
    private final WithdrawalCalculationRules workforceRules;
    private final SalesPipelineCalculationRules calculations;
    private final BusinessCalendar calendar;


    public SalesPipelineForecastService(SalesPipelineMapper mapper, WithdrawalMapper workforce,
        WithdrawalCalculationRules workforceRules, SalesPipelineCalculationRules calculations,
        BusinessCalendar calendar) {
        this.mapper = mapper;
        this.workforce = workforce;
        this.workforceRules = workforceRules;
        this.calculations = calculations;
        this.calendar = calendar;

    }

    public Forecast getCommonSalesPipelineForecast() {
        var loaded = calculations.definitions();
        SalesPipelineCalculationRules.require(loaded, "식-60", "max(0, 1", "소수 첫째 자리");
        SalesPipelineCalculationRules.require(loaded, "식-61", "필요 인원 × 월 MM × 수주 확률", "미충원");
        SalesPipelineCalculationRules.require(loaded, "식-92", "다음 달부터", "예상 금액 ÷ 예상 기간");
        SalesPipelineCalculationRules.require(loaded, "식-93", "첫 부족 월", "권장 시작 월");
        var settings = workforceRules.settings();
        var first = calendar.currentMonth().plusMonths(1);
        var jobs = mapper.jobs();
        BigDecimal threshold = calculations.standard("SHORT_PERSON");
        int lead = calculations.standard("HIRE_LEAD_MON").intValueExact();
        var months = new ArrayList<ForecastMonth>();
        var firstShortage = new LinkedHashMap<String, YearMonth>();
        var maximum = new HashMap<String, BigDecimal>();
        for (int offset = 0; offset < 6; offset++) {
            YearMonth month = first.plusMonths(offset);
            var revenue = mapper.revenue(query(null, month, first));
            BigDecimal totalSupply = BigDecimal.ZERO, totalDemand = BigDecimal.ZERO;
            for (var job : jobs) {
                var filter = new MonthFilter(job.jobCode(), calendar.toDatabaseMonth(month),
                    month.atDay(1), month.atEndOfMonth());
                var extensions = workforce.extensions(filter);
                var people = workforce.supply(filter).stream().map(person -> new SupplyPerson(person.confirmedMm(),
                    extensions.stream().filter(e -> person.developerId().equals(e.developerId()))
                        .map(e -> new Extension(e.monthlyMm(), e.extensionPossibilityCode())).toList())).toList();
                // 운영 기준은 예측 월의 미래값이 아니라 이번 한국 월의 적용값이다.
                BigDecimal supply = settings.supply(people, calendar.currentMonth());
                var opportunities = workforce.opportunityDemand(filter).stream()
                    .map(n -> new OpportunityDemand(n.requiredCount(), n.monthlyMm(), n.winningProbability())).toList();
                var projects = mapper.projectDemand(query(job.jobCode(), month, first)).stream()
                    .map(p -> new ProjectDemand(p.plannedMm(), p.confirmedMm(), p.remainingMonths())).toList();
                BigDecimal demand = settings.demand(opportunities, projects);
                totalSupply = totalSupply.add(supply);
                totalDemand = totalDemand.add(demand);
                BigDecimal shortage = demand.subtract(supply);
                maximum.merge(job.jobCode(), shortage, BigDecimal::max);
                if (shortage.compareTo(threshold) > 0) firstShortage.putIfAbsent(job.jobCode(), month);
            }
            months.add(new ForecastMonth(month.toString(), revenue.projectRevenue(), revenue.weightedOpportunityRevenue(),
                revenue.projectRevenue().add(revenue.opportunityRevenue()), totalDemand, totalSupply));
        }
        var hiring = new ArrayList<HiringReview>();
        for (var job : jobs) {
            var shortage = firstShortage.get(job.jobCode());
            if (shortage == null) continue;
            var recommended = shortage.minusMonths(lead);
            hiring.add(new HiringReview(job.jobCode(), job.jobName(), shortage.toString(),
                maximum.get(job.jobCode()), recommended.isBefore(calendar.currentMonth()) ? "지금" : recommended.toString(),
                mapper.candidates(job.jobCode())));
        }
        return new Forecast(List.copyOf(months), List.copyOf(hiring));
    }

    private MonthQuery query(String job, YearMonth month, YearMonth horizon) {
        return new MonthQuery(job, calendar.toDatabaseMonth(month), month.atDay(1), month.atEndOfMonth(), horizon.atDay(1));
    }

}
