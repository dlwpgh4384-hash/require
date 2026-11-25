package com.company.common.salespipeline.service;

import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.mapper.SalesPipelineRows.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import com.company.common.withdrawals.service.WithdrawalCalculationRules.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
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

    public Forecast getCommonSalesPipelineForecast() { return getCommonSalesPipelineForecast(null); }

    /** 시작 달(yyyy-MM)은 이번 달 또는 다음 달만 받는다. 없으면 다음 달(영업 파이프라인 UI-29), 인력 운영 보드(UI-28)는 이번 달. */
    public Forecast getCommonSalesPipelineForecast(String startMonth) {
        var base = calendar.currentMonth();
        var next = base.plusMonths(1);
        YearMonth first = next;
        if (startMonth != null) {
            if (!startMonth.matches("\\d{4}-\\d{2}"))
                throw CodeFailure.invalid("startMonth", "시작 달은 yyyy-MM 모양으로 넣어 주세요.");
            try {
                first = YearMonth.parse(startMonth);
            } catch (DateTimeParseException e) {
                throw CodeFailure.invalid("startMonth", "시작 달은 yyyy-MM 모양으로 넣어 주세요.");
            }
            if (!first.equals(base) && !first.equals(next))
                throw CodeFailure.invalid("startMonth", "시작 달은 이번 달 또는 다음 달만 고를 수 있습니다.");
        }
        var loaded = calculations.definitions();
        SalesPipelineCalculationRules.require(loaded, "식-60", "max(0, 1", "소수 첫째 자리");
        SalesPipelineCalculationRules.require(loaded, "식-61", "필요 인원 × 월 MM × 수주 확률", "미충원");
        SalesPipelineCalculationRules.require(loaded, "식-92", "다음 달부터", "예상 금액 ÷ 예상 기간");
        SalesPipelineCalculationRules.require(loaded, "식-93", "첫 부족 월", "권장 시작 월");
        var jobs = mapper.jobs();
        BigDecimal threshold = calculations.standard("SHORT_PERSON");
        int lead = calculations.standard("HIRE_LEAD_MON").intValueExact();
        var windows = new ArrayList<MonthWindow>();
        for (int offset = 0; offset < 6; offset++) {
            YearMonth month = first.plusMonths(offset);
            windows.add(new MonthWindow(calendar.toDatabaseMonth(month), month.atDay(1), month.atEndOfMonth()));
        }
        // 6개월 × 직무를 묶음 조회 몇 번으로 읽고(달·직무마다 따로 읽지 않음) 아래에서 달·직무로 나눈다.
        // 식-92 턴키 나눗수와 식-61 미충원 나눗수는 시작 달과 상관없이 다음 달부터 센다(시안 unfilled f1).
        var revenues = mapper.revenues(windows, next.atDay(1)).stream()
            .collect(Collectors.toMap(MonthRevenue::month, r -> r, (a, b) -> a));
        var workforce = workforceForecast(windows, jobs);
        var opportunityRows = mapper.opportunityRevenues(windows);
        var months = new ArrayList<ForecastMonth>();
        var firstShortage = new LinkedHashMap<String, YearMonth>();
        var maximum = new HashMap<String, BigDecimal>();
        for (int offset = 0; offset < windows.size(); offset++) {
            YearMonth month = first.plusMonths(offset);
            String dbMonth = windows.get(offset).month();
            var revenue = revenues.get(dbMonth);
            var workforceMonth = workforce.get(offset);
            for (var job : workforceMonth.jobs()) {
                BigDecimal shortage = job.demand().subtract(job.supply());
                maximum.merge(job.jobCode(), shortage, BigDecimal::max);
                if (shortage.compareTo(threshold) > 0) firstShortage.putIfAbsent(job.jobCode(), month);
            }
            BigDecimal project = revenue == null ? BigDecimal.ZERO : revenue.projectRevenue();
            BigDecimal weighted = revenue == null ? BigDecimal.ZERO : revenue.weightedOpportunityRevenue();
            BigDecimal opportunity = revenue == null ? BigDecimal.ZERO : revenue.opportunityRevenue();
            months.add(new ForecastMonth(month.toString(), won(project), won(weighted), won(project.add(opportunity)),
                mm(workforceMonth.demand()), mm(workforceMonth.supply())));
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

    public List<WorkforceMonth> getWorkforceForecast(List<MonthWindow> months) {
        // 영업 파이프라인 예측(UI-29)과 같이 식-60·식-61 정의를 먼저 확인한다(인력 수요 예측 UI-17 입구).
        var loaded = calculations.definitions();
        SalesPipelineCalculationRules.require(loaded, "식-60", "max(0, 1", "소수 첫째 자리");
        SalesPipelineCalculationRules.require(loaded, "식-61", "필요 인원 × 월 MM × 수주 확률", "미충원");
        return workforceForecast(List.copyOf(months), mapper.jobs());
    }

    private List<WorkforceMonth> workforceForecast(List<MonthWindow> windows, List<Job> jobs) {
        return workforceForecast(windows, jobs, null);
    }

    public List<WorkforceMonth> getWorkforceForecast(List<MonthWindow> months, BigDecimal probabilityOverride) {
        if (probabilityOverride != null && (probabilityOverride.signum() < 0
                || probabilityOverride.compareTo(BigDecimal.valueOf(100)) > 0))
            throw CodeFailure.invalid("probability", "확률 범위를 확인해 주세요.");
        var loaded = calculations.definitions();
        SalesPipelineCalculationRules.require(loaded, "식-60", "max(0, 1", "소수 첫째 자리");
        SalesPipelineCalculationRules.require(loaded, "식-61", "필요 인원 × 월 MM × 수주 확률", "미충원");
        return workforceForecast(List.copyOf(months), mapper.jobs(), probabilityOverride);
    }

    private List<WorkforceMonth> workforceForecast(List<MonthWindow> windows, List<Job> jobs,
        BigDecimal probabilityOverride) {
        if (windows.isEmpty()) return Collections.emptyList();
        var base = calendar.currentMonth();
        var settings = workforceRules.settings();
        var supply = mapper.supply(windows).stream()
            .collect(Collectors.groupingBy(r -> key(r.month(), r.jobCode()), LinkedHashMap::new, Collectors.toList()));
        var extensions = mapper.extensions(windows, base.atDay(1)).stream()
            .collect(Collectors.groupingBy(r -> key(r.month(), r.jobCode()) + "|" + r.developerId(),
                LinkedHashMap::new, Collectors.toList()));
        var opportunities = mapper.opportunityDemand(windows).stream()
            .collect(Collectors.groupingBy(r -> key(r.month(), r.jobCode()), LinkedHashMap::new, Collectors.toList()));
        var projects = mapper.projectDemand(windows, base.plusMonths(1).atDay(1)).stream()
            .collect(Collectors.groupingBy(r -> key(r.month(), r.jobCode()), LinkedHashMap::new, Collectors.toList()));
        var result = new ArrayList<WorkforceMonth>();
        for (var window : windows) {
            BigDecimal totalSupply = BigDecimal.ZERO, totalDemand = BigDecimal.ZERO, unfilled = BigDecimal.ZERO;
            var jobValues = new ArrayList<WorkforceJob>();
            for (var job : jobs) {
                String key = key(window.month(), job.jobCode());
                var people = supply.getOrDefault(key, List.of()).stream().map(person -> new SupplyPerson(person.confirmedMm(),
                    extensions.getOrDefault(key + "|" + person.developerId(), List.of()).stream()
                        .map(e -> new Extension(e.monthlyMm(), e.extensionPossibilityCode())).toList())).toList();
                BigDecimal jobSupply = settings.supply(people, base);
                var demandRows = opportunities.getOrDefault(key, List.of()).stream()
                    .map(n -> new OpportunityDemand(n.requiredCount(), n.monthlyMm(),
                        probabilityOverride == null ? n.winningProbability() : probabilityOverride)).toList();
                var projectRows = projects.getOrDefault(key, List.of()).stream()
                    .map(p -> new ProjectDemand(p.plannedMm(), p.confirmedMm(), p.remainingMonths())).toList();
                BigDecimal demand = settings.demand(demandRows, projectRows);
                BigDecimal projectDemand = settings.demand(List.of(), projectRows);
                jobValues.add(new WorkforceJob(job.jobCode(), job.jobName(), jobSupply, demand, projectDemand));
                totalSupply = totalSupply.add(jobSupply);
                totalDemand = totalDemand.add(demand);
                unfilled = unfilled.add(projectDemand);
            }
            result.add(new WorkforceMonth(calendarMonth(window.month()), totalSupply, totalDemand,
                unfilled, List.copyOf(jobValues)));
        }
        return List.copyOf(result);
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
