package com.company.common.salespipeline.service;

import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.mapper.SalesPipelineRows.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import com.company.common.withdrawals.service.WithdrawalCalculationRules.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SalesPipelineForecastService {
    private final SalesPipelineMapper mapper;
    private final WithdrawalCalculationRules workforceRules;
    private final SalesPipelineCalculationRules calculations;
    private final BusinessCalendar calendar;

    public SalesPipelineForecastService(SalesPipelineMapper mapper, WithdrawalCalculationRules workforceRules,
        SalesPipelineCalculationRules calculations, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.workforceRules = workforceRules;
        this.calculations = calculations;
        this.calendar = calendar;
    }

    private static String key(String month, String job) { return month + "|" + job; }

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
        var windows = new ArrayList<MonthWindow>();
        for (int offset = 0; offset < 6; offset++) {
            YearMonth month = first.plusMonths(offset);
            windows.add(new MonthWindow(calendar.toDatabaseMonth(month), month.atDay(1), month.atEndOfMonth()));
        }
        // 6개월 × 직무를 묶음 조회 몇 번으로 읽고(달·직무마다 따로 읽지 않음) 아래에서 달·직무로 나눈다.
        var revenues = mapper.revenues(windows, first.atDay(1)).stream()
            .collect(Collectors.toMap(MonthRevenue::month, r -> r, (a, b) -> a));
        var supply = mapper.supply(windows).stream()
            .collect(Collectors.groupingBy(r -> key(r.month(), r.jobCode()), LinkedHashMap::new, Collectors.toList()));
        var extensions = mapper.extensions(windows).stream()
            .collect(Collectors.groupingBy(r -> key(r.month(), r.jobCode()) + "|" + r.developerId(),
                LinkedHashMap::new, Collectors.toList()));
        var opportunities = mapper.opportunityDemand(windows).stream()
            .collect(Collectors.groupingBy(r -> key(r.month(), r.jobCode()), LinkedHashMap::new, Collectors.toList()));
        var projects = mapper.projectDemand(windows, first.atDay(1)).stream()
            .collect(Collectors.groupingBy(r -> key(r.month(), r.jobCode()), LinkedHashMap::new, Collectors.toList()));
        var opportunityRows = mapper.opportunityRevenues(windows);
        var months = new ArrayList<ForecastMonth>();
        var firstShortage = new LinkedHashMap<String, YearMonth>();
        var maximum = new HashMap<String, BigDecimal>();
        for (int offset = 0; offset < windows.size(); offset++) {
            YearMonth month = first.plusMonths(offset);
            String dbMonth = windows.get(offset).month();
            var revenue = revenues.get(dbMonth);
            BigDecimal totalSupply = BigDecimal.ZERO, totalDemand = BigDecimal.ZERO;
            for (var job : jobs) {
                String key = key(dbMonth, job.jobCode());
                var people = supply.getOrDefault(key, List.of()).stream().map(person -> new SupplyPerson(person.confirmedMm(),
                    extensions.getOrDefault(key + "|" + person.developerId(), List.of()).stream()
                        .map(e -> new Extension(e.monthlyMm(), e.extensionPossibilityCode())).toList())).toList();
                // 운영 기준은 예측 월의 미래값이 아니라 이번 한국 월의 적용값이다.
                BigDecimal jobSupply = settings.supply(people, calendar.currentMonth());
                var demandRows = opportunities.getOrDefault(key, List.of()).stream()
                    .map(n -> new OpportunityDemand(n.requiredCount(), n.monthlyMm(), n.winningProbability())).toList();
                var projectRows = projects.getOrDefault(key, List.of()).stream()
                    .map(p -> new ProjectDemand(p.plannedMm(), p.confirmedMm(), p.remainingMonths())).toList();
                BigDecimal demand = settings.demand(demandRows, projectRows);
                totalSupply = totalSupply.add(jobSupply);
                totalDemand = totalDemand.add(demand);
                BigDecimal shortage = demand.subtract(jobSupply);
                maximum.merge(job.jobCode(), shortage, BigDecimal::max);
                if (shortage.compareTo(threshold) > 0) firstShortage.putIfAbsent(job.jobCode(), month);
            }
            BigDecimal project = revenue == null ? BigDecimal.ZERO : revenue.projectRevenue();
            BigDecimal weighted = revenue == null ? BigDecimal.ZERO : revenue.weightedOpportunityRevenue();
            BigDecimal opportunity = revenue == null ? BigDecimal.ZERO : revenue.opportunityRevenue();
            months.add(new ForecastMonth(month.toString(), won(project), won(weighted), won(project.add(opportunity)),
                mm(totalDemand), mm(totalSupply)));
        }
        var candidates = mapper.candidates().stream()
            .collect(Collectors.toMap(CandidateCount::jobCode, CandidateCount::candidateCount, Integer::sum));
        var hiring = new ArrayList<HiringReview>();
        for (var job : jobs) {
            var shortage = firstShortage.get(job.jobCode());
            if (shortage == null) continue;
            var recommended = shortage.minusMonths(lead);
            hiring.add(new HiringReview(job.jobCode(), job.jobName(), shortage.toString(),
                mm(maximum.get(job.jobCode())), recommended.isBefore(calendar.currentMonth()) ? "지금" : recommended.toString(),
                candidates.getOrDefault(job.jobCode(), 0)));
        }
        return new Forecast(List.copyOf(months), List.copyOf(hiring), opportunityLines(opportunityRows, months));
    }

    /**
     * 식-92 기회별 줄. 달마다 원 단위로 나누되 줄을 더하면 그 달 영업 기회 합계(weightedOpportunityRevenue)와
     * 꼭 같게 한다: 버림한 뒤 남은 원을 소수 부분이 큰 줄부터 1원씩 준다.
     */
    static List<ForecastOpportunity> opportunityLines(List<OpportunityMonthRevenue> rows, List<ForecastMonth> months) {
        var totals = months.stream().collect(Collectors.toMap(ForecastMonth::month, ForecastMonth::weightedOpportunityRevenue));
        var byMonth = rows.stream().collect(Collectors.groupingBy(r -> calendarMonth(r.month()), LinkedHashMap::new, Collectors.toList()));
        var won = new HashMap<OpportunityMonthRevenue, BigDecimal>();
        byMonth.forEach((month, list) -> {
            var floors = list.stream().map(r -> r.weightedRevenue().setScale(0, RoundingMode.FLOOR)).toList();
            long left = totals.getOrDefault(month, BigDecimal.ZERO)
                .subtract(floors.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).longValue();
            var order = new ArrayList<Integer>();
            for (int i = 0; i < list.size(); i++) order.add(i);
            order.sort(Comparator.comparing((Integer i) -> list.get(i).weightedRevenue().subtract(floors.get(i))).reversed());
            for (int k = 0; k < list.size(); k++) {
                int i = order.get(k);
                won.put(list.get(i), floors.get(i).add(k < left ? BigDecimal.ONE : BigDecimal.ZERO));
            }
        });
        var lines = new LinkedHashMap<String, List<OpportunityMonthRevenue>>();
        rows.forEach(r -> lines.computeIfAbsent(r.opportunityId(), id -> new ArrayList<>()).add(r));
        return lines.values().stream().map(list -> new ForecastOpportunity(list.get(0).opportunityId(),
            list.get(0).opportunityName(), list.get(0).winningProbability(),
            list.stream().map(r -> new ForecastOpportunityMonth(calendarMonth(r.month()), won.get(r))).toList())).toList();
    }
    /** DB 달 YYYYMM → 화면 달 YYYY-MM. */
    private static String calendarMonth(String month) { return month.substring(0, 4) + "-" + month.substring(4); }

    /** 금액은 원 단위 정수(식-00), 반올림. */
    static BigDecimal won(BigDecimal value) { return value.setScale(0, RoundingMode.HALF_UP); }
    /** MM은 소수 둘째 자리 반올림(식-58). 판정(부족 > 0.4)은 반올림 전 값으로 한다. */
    static BigDecimal mm(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
