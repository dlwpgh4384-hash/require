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
        var windows = new ArrayList<ProfitStructurePeriod>(List.of(period, previousYear));
        if (previous != null) windows.add(previous);
        for (var window : windows) {
            for (var month = window.start(); !month.isAfter(window.end()); month = month.plusMonths(1))
                years.merge(month.getYear(), month.getMonthValue(), Math::max);
        }
        var source = new TreeMap<String, Month>();
        years.forEach((year, last) -> source.putAll(monthly.year(year, last, basis.cashBaseYm())));
        var selected = select(period, source);
        var current = project(period, selected);
        var prev = previous == null ? List.<Month>of() : select(previous, source); var prior = select(previousYear, source);
        boolean prevAvailable = available(prev), priorAvailable = available(prior);
        var comparison = new Comparison(prevAvailable ? completeSummary(prev, prev.size()) : null, prevAvailable ? period.previousLabel() : null,
            priorAvailable ? completeSummary(prior, prior.size()) : null, priorAvailable, "%p");
        var bucketMonths = period.buckets().stream().map(bucket -> Map.entry(bucket, select(bucket, source))).toList();
        var numeric = bucketMonths.stream().map(entry -> project(entry.getKey(), entry.getValue())).toList();
        List<List<ProfitStructureMonthlyProfitService.ProjectValue>> bucketProjects = new ArrayList<>();
        for (var entry : bucketMonths) bucketProjects.add(entry.getValue().stream().anyMatch(Month::migrated) ? null
            : entry.getValue().stream().flatMap(month -> month.projects().stream()).toList());
        String notice = current.provisional() ? period.end().getMonthValue()
            + "월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다." : null;
        boolean compositionVisible = current.composition() != null;
        var result = new Result(key(period.start()), key(period.end()), period.name(), current.summary(), comparison,
            current.waterfall(), numeric.stream().map(PeriodRow::trend).toList(), current.composition(), compositionVisible,
            current.trend().referenceRevenue() != null, current.provisional(), notice, numeric,
            ProfitStructureTable.of(numeric, bucketProjects, compositionVisible));
        return new Response<>(true, Map.of("previousPeriodAvailable", prevAvailable), result);
    }
    /** 대시보드(UI-16) 「영업 파트별」: 이 화면 월 보기의 영업 파트별과 같은 달·같은 계산(옮겨 온 달은 빈 목록). */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<PartRow> monthSalesParts(YearMonth month) {
        if (!(periods.metadata().get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis))
            throw CodeFailure.invalid("baseMonth", "기간 기준을 확인해 주세요.");
        definitions.require();
        var source = monthly.year(month.getYear(), month.getMonthValue(), basis.cashBaseYm());
        var selected = select(new ProfitStructurePeriod(month, month, "month"), source);
        return selected.stream().anyMatch(Month::migrated) ? List.of() : salesParts(selected);
    }
    private static boolean available(List<Month> months) { return !months.isEmpty() && months.stream().allMatch(Month::available); }
    private static List<Month> select(ProfitStructurePeriod period, Map<String, Month> source) {
        List<Month> result = new ArrayList<>();
        for (YearMonth month = period.start(); !month.isAfter(period.end()); month = month.plusMonths(1)) {
            var value = source.get(key(month));
            if (value != null) result.add(value);
        }
        return result;
    }
}
