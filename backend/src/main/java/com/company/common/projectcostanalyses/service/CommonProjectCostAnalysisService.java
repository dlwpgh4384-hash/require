package com.company.common.projectcostanalyses.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.ProjectCostAnalysisDefinitions;
import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts.*;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisMapper;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisRows.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.personalcostprofits.service.PersonalCostProfitRules;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static java.math.BigDecimal.ZERO;
import static com.company.common.projectcostanalyses.service.ProjectCostAnalysisRules.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CommonProjectCostAnalysisService {
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("uuuuMM");
    private final ProjectCostAnalysisMapper mapper;
    private final ProjectCostAnalysisDefinitions definitions;
    private final CalculationRuleService calculations;
    private final UserPreferencePeriodBasis periods;
    private final BusinessCalendar calendar;
    private final ObjectMapper json;

    public CommonProjectCostAnalysisService(ProjectCostAnalysisMapper mapper, ProjectCostAnalysisDefinitions definitions,
        CalculationRuleService calculations, UserPreferencePeriodBasis periods, BusinessCalendar calendar, ObjectMapper json) {
        this.mapper = mapper;
        this.definitions = definitions;
        this.calculations = calculations;
        this.periods = periods;
        this.calendar = calendar;
        this.json = json;
    }
    record Analysis(Project project, CommonProjectCostAnalysisRow row, List<Cost> costs,
        List<CommonProjectCostAnalysisMonthlyCost> months, BigDecimal remaining, BigDecimal progress) {}
    record Population(List<Analysis> projects, List<Input> inputs, List<Person> people, ProjectCostAnalysisRules rules) {}

    public CommonProjectCostAnalysisResult getCommonProjectCostAnalyses(Filter filter) {
        var population = population(new Period(filter.year(), filter.baseMonth()));
        validateFilters(filter, mapper.selectCodes());
        var all = population.projects().stream().map(Analysis::row).toList();
        var summary = new CommonProjectCostAnalysisSummary(
            sum(all.stream().map(CommonProjectCostAnalysisRow::cumulativeRevenue).toList()),
            sum(all.stream().map(CommonProjectCostAnalysisRow::cumulativeProfit).toList()),
            (int) all.stream().filter(CommonProjectCostAnalysisRow::lowMargin).count(),
            (int) all.stream().filter(CommonProjectCostAnalysisRow::budgetExceeded).count(),
            sum(all.stream().map(CommonProjectCostAnalysisRow::researchCost).toList()));
        var items = all.stream().filter(r -> blank(filter.projectId()) || r.projectId().equals(filter.projectId()))
            .filter(r -> blank(filter.contractTypeCode()) || r.contractTypeCode().equals(filter.contractTypeCode()))
            .filter(r -> blank(filter.projectStatus()) || r.projectStatus().equals(filter.projectStatus()))
            .filter(r -> !Boolean.TRUE.equals(filter.alertOnly()) || r.alertCount() > 0)
            .sorted(Comparator.comparingInt(CommonProjectCostAnalysisRow::alertCount).reversed()
                .thenComparing(CommonProjectCostAnalysisRow::projectId)).toList();
        return new CommonProjectCostAnalysisResult(summary, items, items.size(), all.stream()
            .map(r -> new ProjectOption(r.projectId(), r.projectName())).toList());
    }
    public CommonProjectCostAnalysisMonthlyCosts getCommonProjectCostAnalysisMonthlyCosts(String projectId, Period period) {
        return new CommonProjectCostAnalysisMonthlyCosts(require(population(period), projectId).months());
    }
    public CommonProjectCostAnalysisWorkforce getCommonProjectCostAnalysisWorkforce(String projectId, Period period) {
        var source = population(period);
        require(source, projectId);
        String ym = YearMonth.of(period.year(), period.baseMonth()).format(YM);
        Map<String, CommonProjectCostAnalysisWorkforceRow> rows = new TreeMap<>();
        for (var input : source.inputs()) {
            if (!input.projectId().equals(projectId) || !input.yearMonth().equals(ym)) continue;
            var person = source.people().stream().filter(p -> p.developerId().equals(input.developerId())).findFirst();
            if (person.isEmpty()) continue;
            String key = input.developerId() + "\u0000" + input.jobName();
            var old = rows.get(key);
            rows.put(key, new CommonProjectCostAnalysisWorkforceRow(input.developerId(), person.get().developerName(),
                input.jobName(), input.mm().add(old == null ? ZERO : old.monthlyMm())));
        }
        return new CommonProjectCostAnalysisWorkforce(new ArrayList<>(rows.values()));
    }
    public CommonProjectCostAnalysisSimulation getCommonProjectCostAnalysisSimulation(String projectId, SimulationInput input) {
        validateSimulation(input);
        var population = population(new Period(input.year(), input.baseMonth()));
        var analysis = require(population, projectId);
        var p = analysis.project();
        if ("IN".equals(p.contractTypeCode())) return new CommonProjectCostAnalysisSimulation(true, ZERO, ZERO, ZERO,
            "내부 프로젝트는 매출·마진 기준이 없습니다.");
        boolean turnkey = "TK".equals(p.contractTypeCode());
        if (!turnkey && (input.monthlyPricePerPerson() == null || input.monthlyPricePerPerson().signum() < 0))
            throw CodeFailure.invalid("monthlyPricePerPerson", "1인 단가를 0 이상의 숫자로 입력해 주세요.");
        String selectedMonth = YearMonth.of(input.year(), input.baseMonth()).format(YM);
        BigDecimal revenue = turnkey ? p.contractAmount() : selectedRevenue(analysis, selectedMonth);
        BigDecimal cost = turnkey ? analysis.row().totalCost() : selectedCost(analysis, selectedMonth);
        if (turnkey) {
            if (analysis.remaining() == null) throw CodeFailure.invalid("monthlyCostPerPerson", "월 인건비 누락 근거를 확인해 주세요.");
            cost = cost.add(analysis.remaining());
        }
        if (revenue == null || revenue.signum() <= 0) throw CodeFailure.invalid("headcountChange", "마진 계산의 매출 기준을 확인해 주세요.");
        BigDecimal nextRevenue = turnkey ? revenue : revenue.add(input.headcountChange().multiply(input.monthlyPricePerPerson()));
        if (nextRevenue.signum() <= 0) throw CodeFailure.invalid("headcountChange", "변경 후 매출이 0보다 커야 합니다.");
        BigDecimal changeCost = input.headcountChange().multiply(input.monthlyCostPerPerson())
            .multiply(turnkey ? BigDecimal.valueOf(input.months()) : BigDecimal.ONE);
        BigDecimal before = rate(revenue, cost), after = rate(nextRevenue, cost.add(changeCost));
        return new CommonProjectCostAnalysisSimulation(true, before, after, after.subtract(before),
            (turnkey ? "턴키 · " : "월단위·파견은 한 달 기준 · ") + population.rules().basis("식-26"));
    }
    public CommonProjectCostAnalysisCauses getCommonProjectCostAnalysisCauses(String projectId, Period period) {
        var population = population(period);
        var a = require(population, projectId);
        var row = a.row();
        List<CommonProjectCostAnalysisCause> causes = new ArrayList<>();
        boolean internal = "IN".equals(a.project().contractTypeCode());
        if (!internal) {
            if (row.plannedMm() != null && row.costPerMm() != null) {
                YearMonth start = YearMonth.from(a.project().startDate());
                long duration = java.time.temporal.ChronoUnit.MONTHS.between(start,YearMonth.from(a.project().endDate()))+1;
                long elapsed = Math.min(duration,java.time.temporal.ChronoUnit.MONTHS.between(start,YearMonth.of(period.year(),period.baseMonth()))+1);
                BigDecimal plannedToDate = row.plannedMm().multiply(BigDecimal.valueOf(elapsed))
                    .divide(BigDecimal.valueOf(duration),16,java.math.RoundingMode.HALF_UP);
                BigDecimal excess = row.actualMm().subtract(plannedToDate).max(ZERO).multiply(row.costPerMm());
                causes.add(cause("투입 초과", excess, "PM: 인원 계획과 투입을 확인해 주세요.", "UI-11", projectId, period));
            }
            BigDecimal priceCost = ZERO;
            for (var cost : a.costs()) {
                if ("FREE".equals(cost.inputTypeCode())) continue;
                var source = population.inputs().stream().filter(i -> i.assignmentId().equals(cost.assignmentId())
                    && i.yearMonth().equals(cost.yearMonth())).findFirst();
                if (source.isPresent() && source.get().unitPrice() != null) {
                    BigDecimal target = calculations.effectiveStandard("TGT_MRGN_RT", YearMonth.parse(cost.yearMonth(), YM)).value();
                    BigDecimal allowance = source.get().unitPrice().multiply(cost.mm())
                        .multiply(BigDecimal.ONE.subtract(target.movePointLeft(2)));
                    priceCost = priceCost.add(cost.labor().subtract(allowance).max(ZERO));
                }
            }
            causes.add(cause("단가 대비 원가", priceCost, "파트장: 단가와 원가를 확인해 주세요.", "UI-32", projectId, period));
            causes.add(cause("무상지원", row.freeSupportCost(), "PM: 무상지원 투입을 확인해 주세요.", "UI-11", projectId, period));
        }
        causes.sort(Comparator.comparing(CommonProjectCostAnalysisCause::amount).reversed()
            .thenComparing(CommonProjectCostAnalysisCause::causeName));
        BigDecimal excess = !internal && "TK".equals(a.project().contractTypeCode()) && a.progress() != null
            && a.project().budget() != null ? row.totalCost().subtract(a.project().budget().multiply(a.progress().movePointLeft(2))) : null;
        BigDecimal remaining = excess == null ? null : excess.subtract(sum(causes.stream().map(CommonProjectCostAnalysisCause::amount).toList()));
        String explanation = remaining != null && remaining.signum() > 0
            ? "세 원인으로 설명되지 않는 나머지 " + remaining.toPlainString() + "원입니다." : null;
        boolean red = row.alertCount() > 0 || excess != null && excess.signum() > 0;
        return new CommonProjectCostAnalysisCauses(causes, excess, explanation, red ? "빨강" : "회색",
            red ? null : "경보가 없는 프로젝트라 참고용");
    }
    private CommonProjectCostAnalysisCause cause(String name, BigDecimal amount, String task,
        String target, String projectId, Period period) {
        try {
            return new CommonProjectCostAnalysisCause(name, amount, task, target,
                json.writeValueAsString(Map.of("projectId", projectId, "year", period.year(), "baseMonth", period.baseMonth())));
        } catch (JsonProcessingException failure) { throw new IllegalStateException(failure); }
    }
    private Population population(Period period) {
        validatePeriod(period);
        var rules = new ProjectCostAnalysisRules(definitions.definitions());
        var metadata = periods.metadata().get("periodBasis");
        if (!(metadata instanceof UserPreferencePeriodBasis.Basis basis)) throw CodeFailure.invalid("year", "기간 기준을 확인해 주세요.");
        var projects = mapper.selectProjects();
        var history = mapper.selectHistory();
        var snapshots = mapper.selectSnapshots(period);
        var inputs = mapper.selectInputs(period);
        var people = mapper.selectPeople();
        var pay = mapper.selectPayroll(period);
        var salaries = mapper.selectSalaries();
        var expenses = mapper.selectExpenses(period);
        var distributions = mapper.selectDistributions(period);
        var progress = mapper.selectProgress(period);
        var leaves = mapper.selectLeaves(period);
        var codes = mapper.selectCodes();
        YearMonth cutoff = YearMonth.of(period.year(), period.baseMonth());
        String end = cutoff.format(YM);
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
            var projectCosts = costs.stream().filter(c -> c.projectId().equals(p.projectId())
                && c.yearMonth().compareTo(YearMonth.from(p.startDate()).format(YM)) >= 0).toList();
            var projectExpenses = expenses.stream().filter(e -> e.projectId().equals(p.projectId())
                && e.yearMonth().compareTo(YearMonth.from(p.startDate()).format(YM)) >= 0).toList();
            BigDecimal cost = sum(projectCosts.stream().map(c -> c.labor().add(c.team()).add(c.expense())).toList())
                .add(sum(projectExpenses.stream().map(Expense::amount).toList()));
            BigDecimal actualMm = sum(projectCosts.stream().map(Cost::mm).toList());
            BigDecimal freeMm = sum(projectCosts.stream().filter(c -> "FREE".equals(c.inputTypeCode())).map(Cost::mm).toList());
            BigDecimal freeCost = sum(projectCosts.stream().filter(c -> "FREE".equals(c.inputTypeCode())).map(Cost::labor).toList());
            boolean internal = "IN".equals(p.contractTypeCode()), turnkey = "TK".equals(p.contractTypeCode());
            BigDecimal revenue = internal ? null : turnkey ? rules.revenue(p, cost)
                : sum(projectCosts.stream().map(Cost::revenue).toList());
            BigDecimal profit = revenue == null ? null : revenue.subtract(cost);
            BigDecimal remaining = ZERO;
            List<String> missing = new ArrayList<>();
            if (turnkey && !"END".equals(p.statusCode())) {
                for (var input : inputs) {
                    if (!input.projectId().equals(p.projectId()) || input.yearMonth().compareTo(end) <= 0 || input.mm().signum() <= 0) continue;
                    var lastPay = pay.stream().filter(v -> v.developerId().equals(input.developerId()) && v.yearMonth().compareTo(end) <= 0)
                        .max(Comparator.comparing(Pay::yearMonth));
                    BigDecimal salary = lastPay.map(v -> "OS".equals(v.employeeTypeCode()) ? v.vendorSupply()
                        : "FL".equals(v.employeeTypeCode()) ? v.salary()
                        : v.salary().add(v.allowance()).add(v.companyInsurance()).add(v.retirementReserve())).orElse(null);
                    if (salary == null) salary = salaries.stream().filter(s -> s.developerId().equals(input.developerId())
                        && s.startMonth().compareTo(input.yearMonth()) <= 0 && (s.endMonth() == null || s.endMonth().compareTo(input.yearMonth()) >= 0))
                        .max(Comparator.comparing(Salary::startMonth)).map(Salary::amount).orElse(null);
                    if (salary == null) missing.add(input.developerId() + " " + input.yearMonth() + " 월 인건비 없음");
                    else remaining = remaining.add(input.mm().multiply(salary));
                }
            }
            if (!missing.isEmpty()) remaining = null;
            BigDecimal expected = turnkey && "ING".equals(p.statusCode()) && remaining != null
                ? rate(p.contractAmount(), cost.add(remaining)) : null;
            BigDecimal monthlyRevenue = sum(projectCosts.stream().filter(c -> c.yearMonth().equals(end)).map(Cost::revenue).toList());
            BigDecimal monthlyCost = sum(projectCosts.stream().filter(c -> c.yearMonth().equals(end))
                .map(c -> c.labor().add(c.team()).add(c.expense())).toList())
                .add(sum(projectExpenses.stream().filter(e -> e.yearMonth().equals(end)).map(Expense::amount).toList()));
            BigDecimal monthlyRate = internal || turnkey ? null : rate(monthlyRevenue, monthlyCost);
            BigDecimal target = calculations.effectiveStandard("TGT_MRGN_RT", cutoff).value();
            BigDecimal margin = turnkey ? expected : monthlyRate;
            BigDecimal difference = margin == null ? null : margin.subtract(target);
            var usage = internal ? null : ratio(cost, p.budget());
            if (usage != null) usage = usage.movePointRight(2);
            boolean low = !internal && lowMargin(margin, target), exceeded = !internal && "빨강".equals(budgetColor(usage));
            List<String> alerts = new ArrayList<>();
            if (low) alerts.add("저마진 경보");
            if (exceeded) alerts.add("예산 초과");
            String composition = "배분 원가 + 팀비 + 프로젝트경비 + 추가 직접 경비(프로젝트·월별 한 번). 마감월은 저장값입니다.";
            composition += " DB 정의: " + rules.basis("식-18") + " · " + rules.basis("식-20")
                + " · " + rules.basis("식-22") + " · " + rules.basis("식-27");
            if (!internal && (p.contractAmount() == null || p.budget() == null)) composition += " 계약·예산의 해당월 이력이 부족해 관련 매출·마진을 표시하지 않습니다.";
            var row = new CommonProjectCostAnalysisRow(p.projectId(), p.projectName(), p.contractTypeCode(), p.contractTypeName(),
                "END".equals(p.statusCode()) ? "종료" : "진행", internal ? null : p.originalAmount(), internal ? null : p.contractAmount(),
                internal ? null : p.plannedMm(), actualMm, revenue, internal ? null : cost, profit, internal ? null : rate(revenue, cost),
                expected, monthlyRate, difference, usage, internal ? null : ratio(revenue, actualMm), internal ? null : ratio(cost, actualMm),
                internal ? null : ratio(profit, actualMm), internal ? null : freeMm, internal ? null : freeCost, internal ? cost : null,
                low, exceeded, alerts.size(), alerts, budgetColor(usage), !missing.isEmpty(),
                missing.isEmpty() ? null : String.join("; ", new LinkedHashSet<>(missing)), composition);
            Set<String> available = new TreeSet<>();
            projectCosts.forEach(c -> available.add(c.yearMonth()));
            projectExpenses.forEach(e -> available.add(e.yearMonth()));
            List<CommonProjectCostAnalysisMonthlyCost> detail = new ArrayList<>();
            for (String month : available) {
                var monthly = projectCosts.stream().filter(c -> c.yearMonth().equals(month)).toList();
                BigDecimal value = sum(monthly.stream().map(c -> c.labor().add(c.team()).add(c.expense())).toList())
                    .add(sum(projectExpenses.stream().filter(e -> e.yearMonth().equals(month)).map(Expense::amount).toList()));
                BigDecimal reference = turnkey ? sum(monthly.stream().map(Cost::revenue).toList()) : null;
                if (turnkey && !closed(month,basis)) reference = distributions.stream()
                    .filter(d -> d.projectId().equals(p.projectId()) && d.yearMonth().equals(month))
                    .findFirst().map(Expense::amount).orElse(reference);
                detail.add(new CommonProjectCostAnalysisMonthlyCost(month, value, reference));
            }
            BigDecimal actualProgress = progress.stream().filter(v -> v.projectId().equals(p.projectId())).findFirst().map(Progress::actualProgress).orElse(null);
            results.add(new Analysis(p, row, projectCosts, detail, remaining, actualProgress));
        }
        return new Population(results, inputs, people, rules);
    }
    private static Project historical(Project p, List<History> changes, YearMonth month) {
        var cutoff = month.atEndOfMonth();
        String period = history(p, changes, "PERIOD", cutoff, p.startDate() + "~" + p.endDate());
        java.time.LocalDate start = p.startDate(), end = p.endDate();
        if (period != null && period.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}~[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            start = java.time.LocalDate.parse(period.substring(0,10)); end = java.time.LocalDate.parse(period.substring(11));
        }
        String status = history(p, changes, "STAT", cutoff, p.statusCode());
        if ("END".equals(status) && end.isAfter(cutoff)) status = "ING";
        return new Project(p.projectId(), p.projectName(), p.contractTypeCode(), p.contractTypeName(), status, start, end,
            p.originalAmount(), historicAmount(p, changes, "AMT", cutoff, p.contractAmount()),
            historicAmount(p, changes, "BUDGET", cutoff, p.budget()),
            historicAmount(p, changes, "TEAM", cutoff, p.team()), historicAmount(p, changes, "PEXP", cutoff, p.expense()),
            p.plannedMm(), p.modifiedDate());
    }
    private static boolean closed(String ym, UserPreferencePeriodBasis.Basis basis) {
        return ym.compareTo(basis.cashBaseYm()) < 0 || basis.closedMonths().contains(ym);
    }
    private void validatePeriod(Period p) {
        if (p.baseMonth() == null || p.baseMonth() < 1 || p.baseMonth() > 12)
            throw CodeFailure.invalid("baseMonth", "기준월을 1~12월에서 선택해 주세요.");
        if (p.year() == null || p.year() < 1 || p.year() > 9999) throw CodeFailure.invalid("year", "연도를 선택해 주세요.");
        if (YearMonth.of(p.year(), p.baseMonth()).isAfter(calendar.currentMonth()))
            throw CodeFailure.invalid("baseMonth", "이번 달까지의 기준월을 선택해 주세요.");
    }
    private static void validateSimulation(SimulationInput input) {
        if (input.headcountChange() == null) throw CodeFailure.invalid("headcountChange", "인원 변화를 입력해 주세요.");
        if (input.monthlyCostPerPerson() == null || input.monthlyCostPerPerson().signum() < 0)
            throw CodeFailure.invalid("monthlyCostPerPerson", "1인 월 원가를 0 이상으로 입력해 주세요.");
        if (input.months() == null || input.months() <= 0) throw CodeFailure.invalid("months", "적용 개월을 양의 정수로 입력해 주세요.");
    }
    private static void validateFilters(Filter f, List<Code> codes) {
        if (!blank(f.projectStatus()) && !Set.of("진행", "종료").contains(f.projectStatus()))
            throw CodeFailure.invalid("projectStatus", "진행 또는 종료를 선택해 주세요.");
        if (!blank(f.contractTypeCode()) && codes.stream().noneMatch(c -> c.groupCode().equals("CTRT_TYPE") && c.code().equals(f.contractTypeCode())))
            throw CodeFailure.invalid("contractTypeCode", "계약유형을 확인해 주세요.");
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
    private static BigDecimal selectedRevenue(Analysis a, String month) {
        return sum(a.costs().stream().filter(c -> c.yearMonth().equals(month)).map(Cost::revenue).toList());
    }
    private static BigDecimal selectedCost(Analysis a, String month) {
        return a.months().stream().filter(m -> m.yearMonth().equals(month))
            .reduce((x,y) -> y).map(CommonProjectCostAnalysisMonthlyCost::cost).orElse(ZERO);
    }
}
