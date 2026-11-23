package com.company.common.profitstructures.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.financeitems.service.FinanceItemRules;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.personalcostprofits.service.PersonalCostProfitRules;
import com.company.common.personalcostprofits.service.PersonalCostProfitRules.Rates;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.mapper.ProfitStructureRows.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static java.math.BigDecimal.ZERO;
import static com.company.common.profitstructures.service.ProfitStructurePeriod.key;

/** Company month boundary shared by readonly financial projections; never materializes a snapshot. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ProfitStructureMonthlyProfitService {
    private final ProfitStructureMapper mapper;
    private final CalculationRuleService calculation;
    public ProfitStructureMonthlyProfitService(ProfitStructureMapper mapper, CalculationRuleService calculation) {
        this.mapper = mapper; this.calculation = calculation;
    }
    public record ProjectValue(ProjectMonth project, BigDecimal revenue, BigDecimal allocatedCost) {}
    public record Month(Snapshot values, List<ProjectValue> projects, BigDecimal referenceRevenue,
        boolean available, boolean migrated, boolean provisional) {}
    private static final class ProjectAmounts {
        BigDecimal revenue = ZERO, cost = ZERO;
    }
    public Map<String, Month> year(int year, int baseMonth, String cashBase) {
        var query = new Query(year, baseMonth);
        var snapshots = mapper.snapshots(query);
        var projects = mapper.projects(query);
        var stored = mapper.costs(query);
        var finance = mapper.amounts(query);
        var people = mapper.people(query);
        var payroll = mapper.payroll(query);
        var classes = mapper.payrollClasses(query);
        var inputs = mapper.assignments(query);
        var leaves = mapper.leaves(query);
        var codes = mapper.codes();
        var fixed = new HashMap<String, Snapshot>();
        for (var row : snapshots) if ("Y".equals(row.fixed())) fixed.put(row.month(), row);
        var annual = mapper.annualTax(String.format("%04d", year));
        boolean unknownFixedTax = fixed.values().stream().anyMatch(row -> row.tax() == null);
        var taxes = unknownFixedTax ? Map.<String, BigDecimal>of() : FinanceItemRules.allocation(year,
            zero(annual), fixed.keySet(), month -> fixed.get(month).tax());
        var result = new LinkedHashMap<String, Month>();
        for (int m = 1; m <= baseMonth; m++) {
            YearMonth month = YearMonth.of(year, m);
            String ym = key(month);
            var saved = fixed.get(ym);
            boolean migrated = saved != null && "Y".equals(saved.migrated()) && ym.compareTo(cashBase) < 0;
            if (migrated) {
                result.put(ym, new Month(saved, List.of(), null, true, true, false));
                continue;
            }
            if (ym.compareTo(cashBase) < 0) {
                var missing = new Snapshot(ym, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, "N", "Y", null);
                result.put(ym, new Month(missing, List.of(), null, false, true, false));
                continue;
            }
            var amounts = new HashMap<String, ProjectAmounts>();
            BigDecimal idle = ZERO, indirect = ZERO, rnd = ZERO, direct = ZERO;
            boolean available = saved != null;
            var monthCosts = stored.stream().filter(row -> row.month().equals(ym)).toList();
            if (saved != null) {
                for (var row : monthCosts) {
                    available = true;
                    if (Set.of("ALLOC", "PRE").contains(row.classification())) {
                        var a = amounts.computeIfAbsent(row.projectId(), id -> new ProjectAmounts());
                        a.cost = a.cost.add(row.labor()); a.revenue = a.revenue.add(row.revenue());
                    }
                }
            } else {
                Rates rates = null;
                for (var original : people) {
                    if (!PersonalCostProfitRules.employed(original, month)) continue;
                    var pay = payroll.stream().filter(row -> row.yearMonth().equals(ym) && row.developerId().equals(original.developerId())).findFirst().orElse(null);
                    var assignments = inputs.stream().filter(row -> row.yearMonth().equals(ym) && row.developerId().equals(original.developerId())).toList();
                    if (pay == null && assignments.isEmpty()) continue;
                    available = true;
                    if (rates == null) rates = new Rates(rate(codes, "ALLOW_TEAM"), rate(codes, "ALLOW_GROUP"), rate(codes, "SEV_RATE"),
                        calculation.effectiveStandard("WTHLD_RT", month).value());
                    String classification = classes.stream().filter(row -> row.month().equals(ym) && row.developerId().equals(original.developerId()))
                        .map(PayrollClass::classification).findFirst().orElse(original.directClassCode());
                    var person = new Person(original.developerId(), original.developerName(), original.affiliationId(), original.affiliationName(),
                        original.positionCode(), original.positionName(), original.employeeTypeCode(), original.employeeTypeName(), original.joinDate(),
                        original.retireDate(), original.jobCode(), original.jobName(), original.gradeCode(), original.gradeName(), original.salesPartId(),
                        classification, original.managementNumber(), original.salesPartName());
                    boolean leave = PersonalCostProfitRules.leaveAt(leaves.stream().filter(row -> row.developerId().equals(person.developerId())).toList(), month.atEndOfMonth());
                    // Company turnover/reference are computed per project below; avoid the personal TK fallback denominator here.
                    var costInputs = assignments.stream().map(a -> new Assignment(a.yearMonth(), a.developerId(), a.projectId(), a.assignmentId(),
                        a.projectName(), a.jobName(), a.contractTypeCode(), a.inputTypeCode(), a.mm(), a.revenue(), a.team(), a.expense(),
                        a.contractAmount(), a.budget(), ZERO)).toList();
                    for (var cost : PersonalCostProfitRules.live(person, pay, costInputs, ym, rates, leave)) {
                        var total = cost.labor().add(cost.team()).add(cost.expense());
                        if ("INDRCT".equals(classification)) { indirect = indirect.add(total); continue; }
                        if ("IN".equals(cost.contractTypeCode())) { rnd = rnd.add(total); continue; }
                        if ("IDLE".equals(cost.projectId())) { idle = idle.add(total); continue; }
                        var a = amounts.computeIfAbsent(cost.projectId(), id -> new ProjectAmounts());
                        a.cost = a.cost.add(cost.labor()); a.revenue = a.revenue.add(cost.revenue());
                        direct = direct.add(cost.team()).add(cost.expense());
                    }
                }
            }
            List<ProjectValue> values = new ArrayList<>();
            BigDecimal revenue = ZERO, allocated = ZERO, reference = ZERO;
            for (var p : projects) {
                if (!p.month().equals(ym)) continue;
                if (p.billed() != null || p.directExpense() != null || p.distribution() != null) available = true;
                if ("IN".equals(p.contractType())) {
                    if (saved == null) rnd = rnd.add(zero(p.directExpense()));
                    continue;
                }
                var a = amounts.getOrDefault(p.projectId(), new ProjectAmounts());
                BigDecimal sales = "TK".equals(p.contractType()) ? zero(p.billed()) : a.revenue;
                if (saved == null) direct = direct.add(zero(p.directExpense()));
                revenue = revenue.add(sales); allocated = allocated.add(a.cost);
                BigDecimal ref = sales;
                if ("TK".equals(p.contractType())) {
                    if (p.distribution() != null) ref = p.distribution();
                    else if (a.cost.signum() == 0) ref = ZERO;
                    else if (p.budget() == null || p.budget().signum() == 0) ref = null;
                    else ref = a.cost.multiply(p.contractAmount()).divide(p.budget(), 0, java.math.RoundingMode.DOWN);
                }
                reference = reference == null || ref == null ? null : reference.add(ref);
                if (amounts.containsKey(p.projectId()) || p.billed() != null)
                    values.add(new ProjectValue(p, sales, a.cost));
            }
            var monthFinance = finance.stream().filter(row -> row.month().equals(ym)).toList();
            available |= !monthFinance.isEmpty() || annual != null;
            if (saved == null) {
                BigDecimal cogs = allocated.add(idle).add(direct), gross = revenue.subtract(cogs);
                BigDecimal sga = sum(monthFinance, "SGA").add(indirect).add(rnd);
                BigDecimal adjustment = sum(monthFinance, "ADJUSTMENT"), operating = gross.subtract(sga).add(adjustment);
                BigDecimal income = sum(monthFinance, "NOPR_IN"), expense = sum(monthFinance, "NOPR_OUT");
                BigDecimal tax = taxes.get(ym), net = tax == null ? null : operating.add(income).subtract(expense).subtract(tax);
                saved = new Snapshot(ym, revenue, allocated, idle, direct, cogs, gross, sga, adjustment, operating,
                    income, expense, tax, net, "N", "N", null, sum(monthFinance, "SGA"), indirect, rnd);
            }
            result.put(ym, new Month(saved, List.copyOf(values), reference, available, false, !"Y".equals(saved.fixed())));
        }
        return result;
    }
    private static BigDecimal sum(List<Amount> rows, String category) {
        return rows.stream().filter(row -> category.equals(row.category())).map(Amount::amount).reduce(ZERO, BigDecimal::add);
    }
    private static BigDecimal rate(List<Code> rows, String code) {
        return rows.stream().filter(row -> "ALWNC".equals(row.groupCode()) && code.equals(row.code())).map(Code::value).filter(Objects::nonNull).findFirst().orElse(ZERO);
    }
    private static BigDecimal zero(BigDecimal value) { return value == null ? ZERO : value; }
}
