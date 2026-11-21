package com.company.common.profitstructures.service;

import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.dto.ProfitStructureContracts.*;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.Month;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.dto.UserContracts.Response;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.profitstructures.service.ProfitStructurePeriod.key;
import static com.company.common.profitstructures.service.ProfitStructureRules.*;

@Service
public class CommonProfitStructureService {
    private final ProfitStructureMonthlyProfitService monthly;
    private final ProfitStructureDefinitions definitions;
    private final UserPreferencePeriodBasis periods;
    public CommonProfitStructureService(ProfitStructureMonthlyProfitService monthly, ProfitStructureDefinitions definitions,
        UserPreferencePeriodBasis periods) { this.monthly = monthly; this.definitions = definitions; this.periods = periods; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Response<Result> getCommonProfitStructures(Filter filter) {
        var period = ProfitStructurePeriod.of(filter);
        var metadata = periods.metadata();
        if (!(metadata.get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis))
            throw CodeFailure.invalid("baseMonth", "기간 기준을 확인해 주세요.");
        if (filter.year() < Math.min(2025, Integer.parseInt(basis.cashBaseYm().substring(0, 4)))
            || key(period.end()).compareTo(basis.latestYm()) > 0)
            throw CodeFailure.invalid("baseMonth", "조회할 수 있는 기준월을 선택해 주세요.");
        definitions.require();
        var previous = period.previous(); var previousYear = period.previousYear();
        Map<Integer, Integer> years = new TreeMap<>();
        for (var window : List.of(period, previous, previousYear)) {
            for (var month = window.start(); !month.isAfter(window.end()); month = month.plusMonths(1))
                years.merge(month.getYear(), month.getMonthValue(), Math::max);
        }
        var source = new TreeMap<String, Month>();
        years.forEach((year, last) -> source.putAll(monthly.year(year, last, basis.cashBaseYm())));
        var selected = select(period, source);
        var current = project(period, selected);
        var prev = select(previous, source); var prior = select(previousYear, source);
        boolean prevAvailable = available(prev), priorAvailable = available(prior);
        var comparison = new Comparison(summary(prev), priorAvailable ? summary(prior) : null, priorAvailable, "%p");
        var numeric = period.buckets().stream().map(bucket -> project(bucket, select(bucket, source))).toList();
        String notice = current.provisional() ? period.end().getMonthValue()
            + "월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다." : null;
        var result = new Result(key(period.start()), key(period.end()), current.summary(), comparison, current.waterfall(),
            numeric.stream().map(PeriodRow::trend).toList(), current.composition(), current.composition() != null,
            current.trend().referenceRevenue() != null, current.provisional(), notice, numeric);
        return new Response<>(true, Map.of("previousPeriodAvailable", prevAvailable), result);
    }
    private static boolean available(List<Month> months) { return months.stream().anyMatch(Month::available); }
    private static List<Month> select(ProfitStructurePeriod period, Map<String, Month> source) {
        List<Month> result = new ArrayList<>();
        for (YearMonth month = period.start(); !month.isAfter(period.end()); month = month.plusMonths(1)) {
            var value = source.get(key(month));
            if (value != null) result.add(value);
        }
        return result;
    }
}
