package com.company.common.commonops.service;

import com.company.common.common.CandidateDefinitions;
import com.company.common.commonops.dto.CandidateOverviewContracts.*;
import com.company.common.commonops.mapper.CandidateOverviewMapper;
import com.company.common.commonops.mapper.CandidateOverviewMapper.Dates;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.MonthFilter;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import com.company.common.withdrawals.service.WithdrawalCalculationRules.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CandidateOverviewService {
    private final CandidateOverviewMapper mapper;
    private final WithdrawalMapper forecast;
    private final WithdrawalCalculationRules calculations;
    private final CandidateDefinitions definitions;
    private final BusinessCalendar calendar;

    public CandidateOverviewService(CandidateOverviewMapper mapper, WithdrawalMapper forecast,
        WithdrawalCalculationRules calculations, CandidateDefinitions definitions, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.forecast = forecast;
        this.calculations = calculations;
        this.definitions = definitions;
        this.calendar = calendar;
    }

    public Summary getCommonCandidateSummary() {
        var today = calendar.today();
        return mapper.summary(new Dates(YearMonth.from(today).plusMonths(1).atDay(1), today.minusDays(30)));
    }

    public Shortages getCommonCandidateShortages() {
        definitions.definitions();
        var settings = calculations.settings();
        var candidates = mapper.activeCandidates();
        var result = new ArrayList<Shortage>();
        YearMonth first = YearMonth.from(calendar.today()).plusMonths(1);
        for (var job : mapper.dictionary("JOB")) {
            YearMonth shortageStart = null;
            BigDecimal maximum = BigDecimal.ZERO;
            for (int i = 0; i < 6; i++) {
                YearMonth month = first.plusMonths(i);
                var filter = new MonthFilter(job.code(), calendar.toDatabaseMonth(month), month.atDay(1), month.atEndOfMonth());
                var extensions = forecast.extensions(filter);
                var people = forecast.supply(filter).stream().map(person -> new SupplyPerson(person.confirmedMm(),
                    extensions.stream().filter(row -> row.developerId().equals(person.developerId()))
                        .map(row -> new Extension(row.monthlyMm(), row.extensionPossibilityCode())).toList())).toList();
                var demand = forecast.opportunityDemand(filter).stream().map(row ->
                    new OpportunityDemand(row.requiredCount(), row.monthlyMm(), row.winningProbability())).toList();
                BigDecimal shortage = settings.demand(demand, List.of()).subtract(settings.supply(people, month));
                if (shortage.signum() > 0) {
                    if (shortageStart == null) shortageStart = month;
                    maximum = maximum.max(shortage);
                }
            }
            if (shortageStart != null) {
                var start = shortageStart.atDay(1);
                int total = Math.toIntExact(candidates.stream().filter(c -> c.jobCode().equals(job.code())).count());
                int available = Math.toIntExact(candidates.stream().filter(c -> c.jobCode().equals(job.code())
                    && !c.availableDate().isAfter(start)).count());
                result.add(new Shortage(job.code(), job.name(), shortageStart.toString(), maximum, available, total));
            }
        }
        result.sort(Comparator.comparing(Shortage::shortageStartMonth).thenComparing(Shortage::jobCode));
        return new Shortages(List.copyOf(result));
    }
}
