package com.company.common.projectcostanalyses.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.ProjectCostAnalysisDefinitions;
import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts.*;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisMapper;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisRows.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.personalcostprofits.service.PersonalCostProfitRules;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static java.math.BigDecimal.ZERO;
import static com.company.common.projectcostanalyses.service.ProjectCostAnalysisRules.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CommonProjectCostAnalysisService {
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("uuuuMM");
    private static final Comparator<String> KOREAN = Collator.getInstance(Locale.KOREAN)::compare;
    private final ProjectCostAnalysisMapper mapper;
    private final ProjectCostAnalysisDefinitions definitions;
    private final CalculationRuleService calculations;
    private final UserPreferencePeriodBasis periods;
    private final ObjectMapper json;

    public CommonProjectCostAnalysisService(ProjectCostAnalysisMapper mapper, ProjectCostAnalysisDefinitions definitions,
        CalculationRuleService calculations, UserPreferencePeriodBasis periods, ObjectMapper json) {
        this.mapper = mapper;
        this.definitions = definitions;
        this.calculations = calculations;
        this.periods = periods;
        this.json = json;
    }
    record Analysis(Project project, CommonProjectCostAnalysisRow row, List<Cost> costs, List<Expense> expenses,
        BigDecimal remaining, BigDecimal progress, List<CommonProjectCostAnalysisCause> causes) {}
    record Population(List<Analysis> projects, List<Input> inputs, List<Person> people, List<Code> codes,
        List<Expense> distributions, ProjectCostAnalysisRules rules, YearMonth cutoff, BigDecimal target,
        BigDecimal overRate, String notice) {}
    /** 식-20 남은 예상 원가: 금액, 센 사람 수, 단가 없음 사람 이름. */
    record Remaining(BigDecimal amount, int people, List<String> missing) {}

    public CommonProjectCostAnalysisResult getCommonProjectCostAnalyses(Filter filter) {
        var population = population(new Period(filter.year(), filter.baseMonth()));
        validateFilters(filter, population.codes(), population);
        var all = population.projects().stream().map(Analysis::row).toList();
        var summary = new CommonProjectCostAnalysisSummary(
            sum(all.stream().map(CommonProjectCostAnalysisRow::cumulativeRevenue).toList()),
            sum(all.stream().filter(r -> !"IN".equals(r.contractTypeCode()))
                .map(CommonProjectCostAnalysisRow::cumulativeProfit).toList()),
            (int) all.stream().filter(CommonProjectCostAnalysisRow::lowMargin).count(),
            (int) all.stream().filter(CommonProjectCostAnalysisRow::budgetExceeded).count(),
            sum(all.stream().map(CommonProjectCostAnalysisRow::researchCost).toList()));
        // 시안: 경보 많은 순, 같으면 프로젝트 이름(가나다) 순
        var items = all.stream().filter(r -> blank(filter.projectId()) || r.projectId().equals(filter.projectId()))
            .filter(r -> blank(filter.contractTypeCode()) || r.contractTypeCode().equals(filter.contractTypeCode()))
            .filter(r -> blank(filter.projectStatus()) || r.projectStatus().equals(filter.projectStatus()))
            .filter(r -> !Boolean.TRUE.equals(filter.alertOnly()) || r.alertCount() > 0)
            .sorted(Comparator.comparingInt(CommonProjectCostAnalysisRow::alertCount).reversed()
                .thenComparing(CommonProjectCostAnalysisRow::projectName, KOREAN)
                .thenComparing(CommonProjectCostAnalysisRow::projectId)).toList();
        var options = all.stream().map(r -> new ProjectOption(r.projectId(), r.projectName()))
            .sorted(Comparator.comparing(ProjectOption::projectName, KOREAN).thenComparing(ProjectOption::projectId)).toList();
        return new CommonProjectCostAnalysisResult(summary, items, items.size(), options,
            population.target(), population.notice());
    }
    /**
     * 프로젝트별 예산 소진율(식-19, 목록 「예산 소진율」과 같은 값). 프로젝트 현황판의 원가 사용률(식-75 「예산 소진율과
     * 같은 값」)이 같은 계산을 쓰게 한다(2026-11-24 사용자 결정). 값이 없는 프로젝트는 빠진다.
     */
    public java.util.Map<String, BigDecimal> budgetUsageRates(int year, int baseMonth) {
        var rates = new java.util.HashMap<String, BigDecimal>();
        for (var analysis : population(new Period(year, baseMonth)).projects())
            if (analysis.row().budgetUsageRate() != null) rates.put(analysis.row().projectId(), analysis.row().budgetUsageRate());
        return rates;
    }
    /** 상세 창 월별 원가: 기준월까지 최근 12달(경과한 달만), 턴키 배분 기준 매출은 식-25. */
    public CommonProjectCostAnalysisMonthlyCosts getCommonProjectCostAnalysisMonthlyCosts(String projectId, Period period) {
        var population = population(period);
        var a = require(population, projectId);
        var p = a.project();
        boolean turnkey = "TK".equals(p.contractTypeCode()), internal = "IN".equals(p.contractTypeCode());
        YearMonth first = YearMonth.from(p.startDate());
        YearMonth last = YearMonth.from(p.endDate()).isBefore(population.cutoff()) ? YearMonth.from(p.endDate()) : population.cutoff();
        List<String> months = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) months.add(m.format(YM));
        Map<String, BigDecimal> cost = new LinkedHashMap<>(), mm = new HashMap<>(), revenue = new HashMap<>();
        for (String month : months) {
            var monthly = a.costs().stream().filter(c -> c.yearMonth().equals(month)).toList();
            cost.put(month, sum(monthly.stream().map(c -> c.labor().add(c.team()).add(c.expense())).toList())
                .add(sum(a.expenses().stream().filter(e -> e.yearMonth().equals(month)).map(Expense::amount).toList())));
            mm.put(month, sum(monthly.stream().map(Cost::mm).toList()));
            revenue.put(month, sum(monthly.stream().map(Cost::revenue).toList()));
        }
        Map<String, BigDecimal> reference = turnkey ? reference(p, a, population, months, cost) : Map.of();
        List<CommonProjectCostAnalysisMonthlyCost> items = new ArrayList<>();
        // 작업중 달에 급여가 없으면 그 달 마진율은 「–」(목록 월간 마진과 같은 규칙, 2026-11-20 결정 ⑧)
        String unpaid = population.notice() == null ? null : population.cutoff().format(YM);
        for (String month : months.subList(Math.max(0, months.size() - 12), months.size())) {
            BigDecimal sales = internal || turnkey ? null : revenue.get(month);
            BigDecimal ref = turnkey ? reference.get(month) : null;
            BigDecimal basis = turnkey ? ref : sales;
            items.add(new CommonProjectCostAnalysisMonthlyCost(month, mm.get(month), cost.get(month), sales, ref,
                internal || month.equals(unpaid) || basis == null || basis.signum() == 0 ? null : rate(basis, cost.get(month))));
        }
        return new CommonProjectCostAnalysisMonthlyCosts(items);
    }
    /**
     * 식-25: 그 달 배분표 합계, 없으면 그 달 원가 × (현재 계약금액 ÷ 예산). 배분표가 하나도 없고 진행률이 1을 넘지 않으면
     * 식-57 끝자리 보정으로 마지막 달 = 누적 매출 − 앞 달들의 합(달별 합 = 누적 매출).
     */
    private Map<String, BigDecimal> reference(Project p, Analysis a, Population population, List<String> months,
        Map<String, BigDecimal> cost) {
        Map<String, BigDecimal> table = new HashMap<>();
        population.distributions().stream().filter(d -> d.projectId().equals(p.projectId()))
            .forEach(d -> table.merge(d.yearMonth(), d.amount(), BigDecimal::add));
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        boolean usable = p.contractAmount() != null && p.budget() != null && p.budget().signum() > 0;
        for (String month : months) {
            BigDecimal value = table.containsKey(month) ? table.get(month)
                : usable ? money(cost.get(month).multiply(p.contractAmount()).divide(p.budget(), 16, RoundingMode.HALF_UP)) : null;
            result.put(month, value);
        }
        BigDecimal cumulative = a.row().cumulativeRevenue();
        if (table.isEmpty() && usable && cumulative != null && !months.isEmpty() && !"END".equals(p.statusCode())
            && a.row().totalCost().compareTo(p.budget()) <= 0) {
            String lastMonth = months.get(months.size() - 1);
            BigDecimal before = sum(months.subList(0, months.size() - 1).stream().map(result::get).toList());
            result.put(lastMonth, cumulative.subtract(before));
        }
        return result;
    }
    /** 투입 인력: 기준월 투입을 사람·직무·투입 구분별로 묶고 그 달 배분 원가와 매출을 붙인다. */
    public CommonProjectCostAnalysisWorkforce getCommonProjectCostAnalysisWorkforce(String projectId, Period period) {
        var source = population(period);
        var a = require(source, projectId);
        boolean internal = "IN".equals(a.project().contractTypeCode());
        String ym = source.cutoff().format(YM);
        Map<String, CommonProjectCostAnalysisWorkforceRow> rows = new TreeMap<>();
        for (var input : source.inputs()) {
            if (!input.projectId().equals(projectId) || !input.yearMonth().equals(ym)) continue;
            var person = source.people().stream().filter(p -> p.developerId().equals(input.developerId())).findFirst();
            if (person.isEmpty()) continue;
            var costs = a.costs().stream().filter(c -> c.yearMonth().equals(ym)
                && c.assignmentId().equals(input.assignmentId())).toList();
            BigDecimal labor = sum(costs.stream().map(Cost::labor).toList());
            BigDecimal sales = sum(costs.stream().map(Cost::revenue).toList());
            String key = person.get().developerName() + "\u0000" + input.developerId() + "\u0000" + input.jobName()
                + "\u0000" + input.inputTypeCode();
            var old = rows.get(key);
            rows.put(key, new CommonProjectCostAnalysisWorkforceRow(input.developerId(), person.get().developerName(),
                input.jobName(), input.mm().add(old == null ? ZERO : old.monthlyMm()),
                labor.add(old == null ? ZERO : old.allocatedCost()),
                internal ? null : sales.add(old == null || old.revenue() == null ? ZERO : old.revenue()),
                input.inputTypeCode(), codeName(source.codes(), "INPUT_TYPE", input.inputTypeCode())));
        }
        return new CommonProjectCostAnalysisWorkforce(new ArrayList<>(rows.values()));
    }
    /** 식-26 가상 마진. 턴키는 남은 원가에 인원×1인 월 원가×개월을 더하고, 월단위·파견은 한 달 기준. 저장하지 않는다. */
    public CommonProjectCostAnalysisSimulation getCommonProjectCostAnalysisSimulation(String projectId, SimulationInput input) {
        var period = new Period(input.year(), input.baseMonth());
        validatePeriodFormat(period);
        validateSimulation(input);
        var population = population(period);
        var analysis = require(population, projectId);
        var p = analysis.project();
        if ("IN".equals(p.contractTypeCode())) return new CommonProjectCostAnalysisSimulation(true, ZERO, ZERO, ZERO,
            ZERO, ZERO, population.target());
        boolean turnkey = "TK".equals(p.contractTypeCode());
        // 말이 되는 범위만(2026-11-20 사용자 결정): 지금 투입 인원보다 많이 뺄 수 없고, 개월은 턴키 남은 기간·월단위 12개월까지
        String cutoffYm = population.cutoff().format(YM);
        long currentPeople = population.inputs().stream().filter(i -> i.projectId().equals(projectId) && i.yearMonth().equals(cutoffYm))
            .map(i -> i.developerId()).distinct().count();
        if (input.headcountChange().compareTo(BigDecimal.valueOf(-currentPeople)) < 0)
            throw CodeFailure.invalid("headcountChange", "지금 투입 인원 " + currentPeople + "명까지만 뺄 수 있습니다.");
        int maxMonths = turnkey ? Math.max(1, analysis.row().remainingMonths()) : 12;
        if (input.months() > maxMonths)
            throw CodeFailure.invalid("months", turnkey ? "남은 기간 " + maxMonths + "개월 이하로 입력해 주세요." : "12개월 이하로 입력해 주세요.");
        if (!turnkey && (input.monthlyPricePerPerson() == null || input.monthlyPricePerPerson().signum() < 0))
            throw CodeFailure.invalid("monthlyPricePerPerson", "추가 인원 1인 월 단가를 0 이상으로 입력해 주세요.");
        if (!turnkey && population.notice() != null) throw CodeFailure.invalid("baseMonth", population.notice() + ".");
        String selectedMonth = population.cutoff().format(YM);
        BigDecimal revenue = turnkey ? p.contractAmount() : selectedRevenue(analysis, selectedMonth);
        BigDecimal cost = turnkey ? analysis.row().totalCost().add(analysis.remaining()) : selectedCost(analysis, selectedMonth);
        if (revenue == null || revenue.signum() <= 0) throw CodeFailure.invalid("headcountChange", "마진 계산의 매출 기준을 확인해 주세요.");
        BigDecimal addedRevenue = turnkey ? ZERO : input.headcountChange().multiply(input.monthlyPricePerPerson());
        BigDecimal nextRevenue = revenue.add(addedRevenue);
        if (nextRevenue.signum() <= 0) throw CodeFailure.invalid("headcountChange", "변경 후 매출이 0보다 커야 합니다.");
        BigDecimal changeCost = input.headcountChange().multiply(input.monthlyCostPerPerson())
            .multiply(turnkey ? BigDecimal.valueOf(input.months()) : BigDecimal.ONE);
        BigDecimal before = rate(revenue, cost), after = rate(nextRevenue, cost.add(changeCost));
        return new CommonProjectCostAnalysisSimulation(true, before, after, after.subtract(before),
            money(addedRevenue), money(changeCost), population.target());
    }
    public CommonProjectCostAnalysisCauses getCommonProjectCostAnalysisCauses(String projectId, Period period) {
        var population = population(period);
        var a = require(population, projectId);
        var row = a.row();
        var p = a.project();
        boolean turnkey = "TK".equals(p.contractTypeCode());
        BigDecimal expectedCost = turnkey && a.progress() != null && p.budget() != null
            ? money(p.budget().multiply(a.progress()).movePointLeft(2)) : null;
        BigDecimal excess = expectedCost == null ? null : row.totalCost().subtract(expectedCost);
        BigDecimal explained = sum(a.causes().stream().map(CommonProjectCostAnalysisCause::amount).toList());
        BigDecimal remainder = excess == null || excess.compareTo(explained) <= 0 ? null : excess.subtract(explained);
        boolean red = row.alertCount() > 0 || excess != null && excess.signum() > 0;
        return new CommonProjectCostAnalysisCauses(a.causes(), expectedCost == null ? null : a.progress(), expectedCost,
            row.totalCost(), excess, remainder, red ? "빨강" : "회색", red ? null : "경보가 없는 프로젝트라 참고용",
            population.overRate());
    }
    /** 식-91 세 원인(금액 큰 순). 계획 MM이 없으면 투입 초과는 계산하지 않는다. 무상 지원은 이동 단추가 없다. */
    private List<CommonProjectCostAnalysisCause> causes(Project p, CommonProjectCostAnalysisRow row, List<Cost> costs,
        BigDecimal overRate, Period period) {
        List<CommonProjectCostAnalysisCause> causes = new ArrayList<>();
        if (row.plannedMm() != null && row.costPerMm() != null && row.totalMonths() > 0) {
            BigDecimal plannedToDate = row.plannedMm().multiply(BigDecimal.valueOf(row.elapsedMonths()))
                .divide(BigDecimal.valueOf(row.totalMonths()), 16, RoundingMode.HALF_UP);
            BigDecimal over = row.actualMm().subtract(plannedToDate).max(ZERO);
            causes.add(cause("mm", "투입이 계획보다 많음", money(over.multiply(row.costPerMm())),
                "실제 " + mm(row.actualMm()) + " MM · 지금까지 계획 " + mm(plannedToDate) + " MM"
                    + (over.signum() > 0 ? " (" + mm(over) + " MM 더 씀)" : ""),
                "PM", "인원 계획을 다시 맞추고 남은 기간 투입을 줄입니다", "UI-11", "인원 계획 대비 투입", p.projectId(), period));
        }
        // 단가 대비 원가: 달마다 사람별 max(0, 배분 원가 − 단가 × MM × 기준%). 단가 × MM은 그 달 사람 매출(턴키는 식-55 개인 월 단가)
        Map<String, BigDecimal[]> byPersonMonth = new TreeMap<>();
        for (var cost : costs) {
            if ("FREE".equals(cost.inputTypeCode())) continue;
            var values = byPersonMonth.computeIfAbsent(cost.yearMonth() + "\u0000" + cost.developerId(),
                key -> new BigDecimal[] {ZERO, ZERO});
            values[0] = values[0].add(cost.labor());
            values[1] = values[1].add(PersonalCostProfitRules.zero(cost.revenue()));
        }
        BigDecimal priceCost = ZERO;
        Set<String> people = new TreeSet<>();
        for (var entry : byPersonMonth.entrySet()) {
            BigDecimal gap = entry.getValue()[0].subtract(entry.getValue()[1].multiply(overRate).movePointLeft(2));
            if (gap.signum() > 0) {
                priceCost = priceCost.add(gap);
                people.add(entry.getKey().substring(entry.getKey().indexOf('\u0000') + 1));
            }
        }
        causes.add(cause("pay", "원가가 단가의 " + overRate.stripTrailingZeros().toPlainString() + "%를 넘는 인력",
            money(priceCost), people.size() + "명 — 받는 단가에 비해 급여·원가가 높음",
            "영업 파트장", "계약 갱신 때 단가를 다시 협상하거나 인력 교체를 검토합니다", "UI-32", "단가 재협상 대상",
            p.projectId(), period));
        causes.add(cause("free", "무상 지원", row.freeSupportCost() == null ? ZERO : row.freeSupportCost(),
            mm(row.freeSupportMm() == null ? ZERO : row.freeSupportMm()) + " MM",
            "영업 파트장", "고객과 유상 전환이나 추가 계약을 협의합니다", null, null, p.projectId(), period));
        List<CommonProjectCostAnalysisCause> sorted = new ArrayList<>(causes);
        sorted.sort(Comparator.comparing(CommonProjectCostAnalysisCause::amount).reversed());
        return sorted;
    }
    private CommonProjectCostAnalysisCause cause(String key, String name, BigDecimal amount, String description,
        String owner, String task, String target, String label, String projectId, Period period) {
        try {
            return new CommonProjectCostAnalysisCause(key, name, amount, description, owner, task, target, label,
                target == null ? null
                    : json.writeValueAsString(Map.of("projectId", projectId, "year", period.year(), "baseMonth", period.baseMonth())));
        } catch (JsonProcessingException failure) { throw new IllegalStateException(failure); }
    }
    private Population population(Period period) {
        var basis = validatePeriod(period);
        var rules = new ProjectCostAnalysisRules(definitions.definitions());
        var projects = mapper.selectProjects();
        var history = mapper.selectHistory();
        var snapshots = mapper.selectSnapshots(period);
        var inputs = mapper.selectInputs(period);
        var people = mapper.selectPeople();
        var pay = mapper.selectPayroll(period);
        var salaries = mapper.selectSalaries();
        var expenses = mapper.selectExpenses(period);
        var distributions = mapper.selectDistributions(period);
        var personDistributions = mapper.selectPersonDistributions();
        var progress = mapper.selectProgress(period);
        var leaves = mapper.selectLeaves(period);
        var codes = mapper.selectCodes();
        YearMonth cutoff = YearMonth.of(period.year(), period.baseMonth());
        String end = cutoff.format(YM);
        // 작업중 달에 그 달 급여가 하나도 없으면 월간 마진을 계산하지 않는다(누적 값은 잠정으로 그대로 계산)
        boolean payrollMissing = !closed(end, basis) && pay.stream().noneMatch(v -> v.yearMonth().equals(end));
        String notice = payrollMissing ? cutoff.getMonthValue() + "월 급여가 아직 반영되지 않아 월간 마진을 계산하지 않습니다" : null;
        BigDecimal target = calculations.effectiveStandard("TGT_MRGN_RT", cutoff).value();
        BigDecimal overRate = calculations.effectiveStandard("COST_OVER_UPRC_RT", cutoff).value();
        Map<String, Project> byId = new HashMap<>();
        projects.forEach(p -> byId.put(p.projectId(), p));
        List<Cost> costs = new ArrayList<>();
        for (var snapshot : snapshots) {
            if (!closed(snapshot.yearMonth(), basis)) continue;
            costs.add(new Cost(snapshot.yearMonth(), snapshot.developerId(), snapshot.projectId(), snapshot.assignmentId(),
                "", "", snapshot.contractTypeCode(), snapshot.mm(), snapshot.revenue(), snapshot.labor(),
                snapshot.team(), snapshot.expense(), snapshot.freeMm().signum() > 0 ? "FREE" : "NEW"));
        }
        var months = inputs.stream().map(Input::yearMonth).filter(m -> m.compareTo(end) <= 0).distinct().sorted().toList();
        for (String month : months) {
            if (closed(month, basis)) continue;
            YearMonth ym = YearMonth.parse(month, YM);
            var rates = new PersonalCostProfitRules.Rates(code(codes, "ALLOW_TEAM"), code(codes, "ALLOW_GROUP"),
                code(codes, "SEV_RATE"), calculations.effectiveStandard("WTHLD_RT", ym).value());
            for (var person : people) {
                var assignments = inputs.stream().filter(i -> i.yearMonth().equals(month) && i.developerId().equals(person.developerId()))
                    .filter(i -> byId.containsKey(i.projectId())).map(i -> {
                        var p = historical(byId.get(i.projectId()), history, ym);
                        // Reference allocation revenue is not used to recognize turnkey revenue.
                        return new Assignment(month, i.developerId(), i.projectId(), i.assignmentId(), p.projectName(),
                            i.jobName(), p.contractTypeCode(), i.inputTypeCode(), i.mm(), i.revenue(), p.team(), p.expense(),
                            p.contractAmount() == null || p.budget() == null ? ZERO : p.contractAmount(),
                            p.budget() == null ? ZERO : p.budget(), null);
                    }).toList();
                if (assignments.isEmpty()) continue;
                var payroll = pay.stream().filter(p -> p.developerId().equals(person.developerId()) && p.yearMonth().equals(month)).findFirst().orElse(null);
                costs.addAll(PersonalCostProfitRules.live(person, payroll, assignments, month, rates,
                    PersonalCostProfitRules.leaveAt(leaves.stream().filter(l -> l.developerId().equals(person.developerId())).toList(), ym.atEndOfMonth())));
            }
        }
        List<Analysis> results = new ArrayList<>();
        for (var current : projects) {
            var p = historical(current, history, cutoff);
            if (p.startDate().isAfter(cutoff.atEndOfMonth()) || p.endDate().isBefore(YearMonth.of(period.year(), 1).atDay(1))
                || !Set.of("ING", "END").contains(p.statusCode())) continue;
            String startYm = YearMonth.from(p.startDate()).format(YM);
            var projectCosts = costs.stream().filter(c -> c.projectId().equals(p.projectId())
                && c.yearMonth().compareTo(startYm) >= 0).toList();
            var projectExpenses = expenses.stream().filter(e -> e.projectId().equals(p.projectId())
                && e.yearMonth().compareTo(startYm) >= 0).toList();
            BigDecimal cost = sum(projectCosts.stream().map(c -> c.labor().add(c.team()).add(c.expense())).toList())
                .add(sum(projectExpenses.stream().map(Expense::amount).toList()));
            BigDecimal actualMm = sum(projectCosts.stream().map(Cost::mm).toList());
            BigDecimal freeMm = sum(projectCosts.stream().filter(c -> "FREE".equals(c.inputTypeCode())).map(Cost::mm).toList());
            BigDecimal freeCost = sum(projectCosts.stream().filter(c -> "FREE".equals(c.inputTypeCode())).map(Cost::labor).toList());
            boolean internal = "IN".equals(p.contractTypeCode()), turnkey = "TK".equals(p.contractTypeCode());
            // 기준월 당시 상태: 기준월 1일 전에 끝났으면 종료(시안 psSt)
            boolean ended = p.endDate().isBefore(cutoff.atDay(1));
            BigDecimal revenue = internal ? null : turnkey ? rules.revenue(p, cost)
                : sum(projectCosts.stream().map(Cost::revenue).toList());
            BigDecimal profit = revenue == null ? null : revenue.subtract(cost);
            Remaining remaining = turnkey && !ended
                ? remaining(p, inputs, pay, salaries, personDistributions, people, end) : new Remaining(ZERO, 0, List.of());
            BigDecimal expected = turnkey ? rate(p.contractAmount(), cost.add(remaining.amount())) : null;
            BigDecimal monthlyRevenue = sum(projectCosts.stream().filter(c -> c.yearMonth().equals(end)).map(Cost::revenue).toList());
            BigDecimal monthlyCost = sum(projectCosts.stream().filter(c -> c.yearMonth().equals(end))
                .map(c -> c.labor().add(c.team()).add(c.expense())).toList())
                .add(sum(projectExpenses.stream().filter(e -> e.yearMonth().equals(end)).map(Expense::amount).toList()));
            BigDecimal monthlyRate = internal || turnkey || payrollMissing ? null : rate(monthlyRevenue, monthlyCost);
            BigDecimal margin = turnkey ? expected : monthlyRate;
            BigDecimal difference = margin == null ? null : margin.subtract(target);
            var usage = internal ? null : ratio(cost, p.budget());
            if (usage != null) usage = usage.movePointRight(2);
            boolean low = !internal && lowMargin(margin, target), exceeded = !internal && "빨강".equals(budgetColor(usage));
            List<String> alerts = new ArrayList<>();
            if (low) alerts.add("저마진");
            if (exceeded) alerts.add("예산 초과");
            YearMonth start = YearMonth.from(p.startDate());
            int span = (int) ChronoUnit.MONTHS.between(start, YearMonth.from(p.endDate())) + 1;
            int elapsed = (int) Math.max(0, Math.min(span, ChronoUnit.MONTHS.between(start, cutoff) + 1));
            boolean missing = !remaining.missing().isEmpty();
            String basisText = turnkey && !ended ? "투입 배정의 남은 달 MM × 월 인건비 (" + remaining.people() + "명"
                + (missing ? ", 단가 없음 " + remaining.missing().size() + "명은 0으로 셈" : "") + ")" : null;
            var draft = new CommonProjectCostAnalysisRow(p.projectId(), p.projectName(), p.contractTypeCode(), p.contractTypeName(),
                ended ? "종료" : "진행", internal ? null : p.originalAmount(), internal ? null : p.contractAmount(),
                internal ? null : p.plannedMm(), actualMm, revenue, cost, profit, internal ? null : rate(revenue, cost),
                expected, monthlyRate, difference, usage, internal ? null : money(ratio(revenue, actualMm)),
                internal ? null : money(ratio(cost, actualMm)), internal ? null : money(ratio(profit, actualMm)),
                internal ? null : freeMm, internal ? null : money(freeCost), internal ? cost : null,
                low, exceeded, alerts.size(), alerts, budgetColor(usage), missing,
                missing ? "단가 없음: " + String.join(", ", remaining.missing()) : null,
                turnkey && !ended ? remaining.amount() : null, basisText, null, null, elapsed, span, span - elapsed,
                p.startDate().toString(), p.endDate().toString());
            var causes = internal ? List.<CommonProjectCostAnalysisCause>of() : causes(p, draft, projectCosts, overRate, period);
            var top = causes.stream().filter(c -> c.amount().signum() > 0).findFirst().orElse(null);
            var row = top == null ? draft : withCause(draft, top);
            BigDecimal actualProgress = progress.stream().filter(v -> v.projectId().equals(p.projectId())).findFirst().map(Progress::actualProgress).orElse(null);
            results.add(new Analysis(p, row, projectCosts, projectExpenses, remaining.amount(), actualProgress, causes));
        }
        return new Population(results, inputs, people, codes, distributions, rules, cutoff, target, overRate, notice);
    }
    private static CommonProjectCostAnalysisRow withCause(CommonProjectCostAnalysisRow r, CommonProjectCostAnalysisCause c) {
        return new CommonProjectCostAnalysisRow(r.projectId(), r.projectName(), r.contractTypeCode(), r.contractTypeName(),
            r.projectStatus(), r.originalContractAmount(), r.contractAmount(), r.plannedMm(), r.actualMm(), r.cumulativeRevenue(),
            r.totalCost(), r.cumulativeProfit(), r.cumulativeMarginRate(), r.expectedFinalMarginRate(), r.monthlyMarginRate(),
            r.targetDifference(), r.budgetUsageRate(), r.unitPricePerMm(), r.costPerMm(), r.profitPerMm(), r.freeSupportMm(),
            r.freeSupportCost(), r.researchCost(), r.lowMargin(), r.budgetExceeded(), r.alertCount(), r.alerts(), r.budgetColor(),
            r.missingMonthlyCost(), r.missingMonthlyCostReason(), r.remainingCost(), r.remainingCostBasis(),
            c.causeName(), c.amount(), r.elapsedMonths(), r.totalMonths(), r.remainingMonths(), r.startDate(), r.endDate());
    }
    /**
     * 식-20: 사람마다 남은 달(기준월 다음 달 ~ 철수일, 계약 종료일까지) MM 합 × 월 인건비, 사람별 원 단위 버림.
     * 월 인건비 순서: ① 최근 월 인건비 ② 개발자 풀 월 급여 ③ 배분표 남은 달 금액 ÷ MM × 예산 ÷ 계약금액
     * (예산이 비면 80%) ④ 0과 「단가 없음」.
     */
    private Remaining remaining(Project p, List<Input> inputs, List<Pay> pay, List<Salary> salaries,
        List<PersonDistribution> distributions, List<Person> people, String end) {
        Map<String, List<Input>> byDeveloper = new TreeMap<>();
        for (var input : inputs) {
            if (!input.projectId().equals(p.projectId()) || input.yearMonth().compareTo(end) <= 0 || input.mm().signum() <= 0) continue;
            byDeveloper.computeIfAbsent(input.developerId(), key -> new ArrayList<>()).add(input);
        }
        BigDecimal total = ZERO;
        List<String> missing = new ArrayList<>();
        for (var entry : byDeveloper.entrySet()) {
            String developer = entry.getKey();
            BigDecimal mm = sum(entry.getValue().stream().map(Input::mm).toList());
            TreeSet<String> months = entry.getValue().stream().map(Input::yearMonth).collect(Collectors.toCollection(TreeSet::new));
            String first = months.first();
            BigDecimal labor = pay.stream().filter(v -> v.developerId().equals(developer) && v.yearMonth().compareTo(end) <= 0)
                .max(Comparator.comparing(Pay::yearMonth)).map(CommonProjectCostAnalysisService::labor).orElse(null);
            if (labor == null) labor = salaries.stream().filter(s -> s.developerId().equals(developer)
                    && s.startMonth().compareTo(first) <= 0 && (s.endMonth() == null || s.endMonth().compareTo(first) >= 0))
                .max(Comparator.comparing(Salary::startMonth)).map(Salary::amount).orElse(null);
            if (labor == null) {
                BigDecimal amount = sum(distributions.stream().filter(d -> d.projectId().equals(p.projectId())
                    && developer.equals(d.developerId()) && months.contains(d.yearMonth())).map(PersonDistribution::amount).toList());
                if (amount.signum() > 0) {
                    BigDecimal share = p.budget() != null && p.contractAmount() != null && p.contractAmount().signum() > 0
                        ? p.budget().divide(p.contractAmount(), 16, RoundingMode.HALF_UP) : new BigDecimal("0.8");
                    labor = amount.divide(mm, 16, RoundingMode.HALF_UP).multiply(share);
                }
            }
            if (labor == null) {
                missing.add(people.stream().filter(person -> person.developerId().equals(developer)).findFirst()
                    .map(Person::developerName).orElse(developer));
                continue;
            }
            total = total.add(money(mm.multiply(labor)));
        }
        return new Remaining(total, byDeveloper.size(), missing);
    }
    /** 최근 월 인건비: 외주는 공급가, 프리랜서는 지급액, 그 밖은 급여+수당+회사 4대보험+퇴직금. */
    private static BigDecimal labor(Pay v) {
        var zero = (java.util.function.Function<BigDecimal, BigDecimal>) PersonalCostProfitRules::zero;
        return "OS".equals(v.employeeTypeCode()) ? zero.apply(v.vendorSupply())
            : "FL".equals(v.employeeTypeCode()) ? zero.apply(v.salary())
            : zero.apply(v.salary()).add(zero.apply(v.allowance())).add(zero.apply(v.companyInsurance()))
                .add(zero.apply(v.retirementReserve()));
    }
    private static Project historical(Project p, List<History> changes, YearMonth month) {
        var cutoff = month.atEndOfMonth();
        String period = history(p, changes, "PERIOD", cutoff, p.startDate() + "~" + p.endDate());
        LocalDate start = p.startDate(), end = p.endDate();
        if (period != null && period.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}~[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            start = LocalDate.parse(period.substring(0,10)); end = LocalDate.parse(period.substring(11));
        }
        String status = history(p, changes, "STAT", cutoff, p.statusCode());
        if ("END".equals(status) && end.isAfter(cutoff)) status = "ING";
        return new Project(p.projectId(), p.projectName(), p.contractTypeCode(), p.contractTypeName(), status, start, end,
            p.originalAmount(), historicAmount(p, changes, "AMT", cutoff, p.contractAmount()),
            historicAmount(p, changes, "BUDGET", cutoff, p.budget()),
            historicAmount(p, changes, "TEAM", cutoff, p.team()), historicAmount(p, changes, "PEXP", cutoff, p.expense()),
            p.plannedMm(), p.modifiedDate());
    }
    /** 마감한 달: CASH_BASE_YM 전 달(이관 자료) 또는 마감한 달. AI 분석 도우미 F-03 잠정도 이 규칙을 쓴다. */
    public static boolean closed(String ym, UserPreferencePeriodBasis.Basis basis) {
        return ym.compareTo(basis.cashBaseYm()) < 0 || basis.closedMonths().contains(ym);
    }
    /** 형식만 본다(DB를 읽기 전). */
    private static void validatePeriodFormat(Period p) {
        if (p.baseMonth() == null || p.baseMonth() < 1 || p.baseMonth() > 12)
            throw CodeFailure.invalid("baseMonth", "기준월을 1~12월에서 선택해 주세요.");
        if (p.year() == null || p.year() < 1 || p.year() > 9999) throw CodeFailure.invalid("year", "연도를 선택해 주세요.");
    }
    /** FR-0105: 고를 수 있는 달은 CASH_BASE_YM의 달부터 가장 최근 달(마지막 마감 달 + 1)까지. */
    private UserPreferencePeriodBasis.Basis validatePeriod(Period p) {
        validatePeriodFormat(p);
        var metadata = periods.metadata().get("periodBasis");
        if (!(metadata instanceof UserPreferencePeriodBasis.Basis basis)) throw CodeFailure.invalid("year", "기간 기준을 확인해 주세요.");
        YearMonth month = YearMonth.of(p.year(), p.baseMonth());
        YearMonth latest = YearMonth.parse(basis.latestYm(), YM), start = YearMonth.parse(basis.cashBaseYm(), YM);
        if (month.isAfter(latest))
            throw CodeFailure.invalid(p.year() > latest.getYear() ? "year" : "baseMonth",
                latest.getYear() + "년 " + latest.getMonthValue() + "월까지 선택할 수 있습니다.");
        if (month.isBefore(start))
            throw CodeFailure.invalid(p.year() < start.getYear() ? "year" : "baseMonth",
                start.getYear() + "년 " + start.getMonthValue() + "월부터 선택할 수 있습니다.");
        return basis;
    }
    private static void validateSimulation(SimulationInput input) {
        if (input.headcountChange() == null) throw CodeFailure.invalid("headcountChange", "인원 변화를 입력해 주세요.");
        if (input.monthlyCostPerPerson() == null || input.monthlyCostPerPerson().signum() < 0)
            throw CodeFailure.invalid("monthlyCostPerPerson", "1인 월 원가를 0 이상으로 입력해 주세요.");
        if (input.months() == null || input.months() <= 0) throw CodeFailure.invalid("months", "적용 개월을 1 이상으로 입력해 주세요.");
    }
    private static void validateFilters(Filter f, List<Code> codes, Population population) {
        if (!blank(f.projectStatus()) && !Set.of("진행", "종료").contains(f.projectStatus()))
            throw CodeFailure.invalid("projectStatus", "진행 또는 종료를 선택해 주세요.");
        if (!blank(f.contractTypeCode()) && codes.stream().noneMatch(c -> c.groupCode().equals("CTRT_TYPE") && c.code().equals(f.contractTypeCode())))
            throw CodeFailure.invalid("contractTypeCode", "계약유형을 확인해 주세요.");
        if (!blank(f.projectId()) && population.projects().stream().noneMatch(a -> a.project().projectId().equals(f.projectId())))
            throw CodeFailure.invalid("projectId", "고른 달의 프로젝트에서 선택해 주세요.");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Analysis require(Population source, String projectId) {
        return source.projects().stream().filter(a -> a.project().projectId().equals(projectId)).findFirst()
            .orElseThrow(() -> new CodeFailure(404, "projectId", "프로젝트를 찾을 수 없습니다."));
    }
    private static BigDecimal code(List<Code> codes, String key) {
        return codes.stream().filter(c -> c.code().equals(key)).map(Code::value).filter(Objects::nonNull).findFirst()
            .orElseThrow(() -> CodeFailure.invalid("source", "원가 기준값을 확인해 주세요."));
    }
    private static String codeName(List<Code> codes, String group, String code) {
        return codes.stream().filter(c -> group.equals(c.groupCode()) && Objects.equals(c.code(), code))
            .map(Code::name).filter(Objects::nonNull).findFirst().orElse(code);
    }
    private static String mm(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static BigDecimal selectedRevenue(Analysis a, String month) {
        return sum(a.costs().stream().filter(c -> c.yearMonth().equals(month)).map(Cost::revenue).toList());
    }
    private static BigDecimal selectedCost(Analysis a, String month) {
        return sum(a.costs().stream().filter(c -> c.yearMonth().equals(month)).map(c -> c.labor().add(c.team()).add(c.expense())).toList())
            .add(sum(a.expenses().stream().filter(e -> e.yearMonth().equals(month)).map(Expense::amount).toList()));
    }
}
