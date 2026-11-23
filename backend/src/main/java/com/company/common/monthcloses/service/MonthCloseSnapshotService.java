package com.company.common.monthcloses.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.MonthCloseDefinitions;
import com.company.common.monthcloses.mapper.MonthCloseMapper;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.personalcostprofits.service.PersonalCostProfitRules;
import com.company.common.personalcostprofits.service.PersonalCostProfitRules.Rates;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Query;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

@Service
public class MonthCloseSnapshotService {
    private final MonthCloseMapper mapper;
    private final MonthCloseAssignmentService assignments;
    private final ProfitStructureMapper sources;
    private final ProfitStructureMonthlyProfitService profits;
    private final MonthCloseDefinitions definitions;
    private final CalculationRuleService calculation;
    public MonthCloseSnapshotService(MonthCloseMapper mapper, MonthCloseAssignmentService assignments,
        ProfitStructureMapper sources, ProfitStructureMonthlyProfitService profits,
        MonthCloseDefinitions definitions, CalculationRuleService calculation) {
        this.mapper = mapper;
        this.assignments = assignments;
        this.sources = sources;
        this.profits = profits;
        this.definitions = definitions;
        this.calculation = calculation;
    }
    public void freeze(YearMonth month, String cashBase, String actor) {
        definitions.definitions();
        var assignmentRows = assignments.calculate(month, actor);
        String key = month.toString().replace("-", "");
        mapper.deleteOpenAssignments(key);
        // Reopen turns every fixed row back to FIX_YN='N'; a leftover fixed row would otherwise be a duplicate key.
        if (mapper.fixedAssignmentCount(key) > 0)
            throw CodeFailure.invalid("source", "이 달에 이미 고정된 월별 투입이 남아 있습니다. 마감 해제 상태를 확인해 주세요.");
        for (var row : assignmentRows) affected(mapper.saveAssignment(row));
        var value = profits.year(month.getYear(), month.getMonthValue(), cashBase).get(key);
        if (value == null || value.values().tax() == null)
            throw CodeFailure.invalid("source", "월 손익과 법인세 원천을 확인해 주세요.");
        var query = new Query(month.getYear(), month.getMonthValue());
        var people = sources.people(query);
        var payroll = sources.payroll(query);
        var inputs = sources.assignments(query);
        var leaves = sources.leaves(query);
        var classes = sources.payrollClasses(query);
        var codes = sources.codes();
        mapper.deleteOpenCosts(key);
        if (mapper.fixedCostCount(key) > 0)
            throw CodeFailure.invalid("source", "이 달에 이미 고정된 원가 배분이 남아 있습니다. 마감 해제 상태를 확인해 주세요.");
        Rates rates = null;
        for (var original : people) {
            if (!PersonalCostProfitRules.employed(original, month)) continue;
            var pay = payroll.stream().filter(row -> row.yearMonth().equals(key)
                && row.developerId().equals(original.developerId())).findFirst().orElse(null);
            var selected = inputs.stream().filter(row -> row.yearMonth().equals(key)
                && row.developerId().equals(original.developerId())).toList();
            if (pay == null && selected.isEmpty()) continue;
            if (rates == null) rates = new Rates(rate(codes, "ALLOW_TEAM"), rate(codes, "ALLOW_GROUP"),
                rate(codes, "SEV_RATE"), calculation.effectiveStandard("WTHLD_RT", month).value());
            String classification = classes.stream().filter(row -> row.month().equals(key)
                && row.developerId().equals(original.developerId())).map(row -> row.classification())
                .findFirst().orElse(original.directClassCode());
            var person = new Person(original.developerId(), original.developerName(), original.affiliationId(),
                original.affiliationName(), original.positionCode(), original.positionName(), original.employeeTypeCode(),
                original.employeeTypeName(), original.joinDate(), original.retireDate(), original.jobCode(), original.jobName(),
                original.gradeCode(), original.gradeName(), original.salesPartId(), classification,
                original.managementNumber(), original.salesPartName());
            boolean leave = PersonalCostProfitRules.leaveAt(leaves.stream()
                .filter(row -> row.developerId().equals(person.developerId())).toList(), month.atEndOfMonth());
            // That person's month MM total (the same inputs the live calculation spreads).
            BigDecimal worked = selected.stream().map(row -> row.mm()).filter(mm -> mm != null && mm.signum() > 0)
                .reduce(ZERO, BigDecimal::add);
            for (var cost : PersonalCostProfitRules.live(person, pay, selected, key, rates, leave)) {
                boolean indirect = "INDRCT".equals(classification);
                String category = indirect ? "INDRCT"
                    : "IN".equals(cost.contractTypeCode()) ? "RND"
                    : "IDLE".equals(cost.projectId()) ? "IDLE"
                    : "PRE".equals(cost.inputTypeCode()) ? "PRE" : "ALLOC";
                // DDL TB_CO_MON_COST: fixed rows use PROJ_ID = ASGN_ID = 'INDRCT' (MM 1.0) or 'IDLE' (1.0 − month MM, not below 0).
                var row = !"IDLE".equals(cost.projectId()) ? cost
                    : indirect ? fixedRow(cost, "INDRCT", ONE) : fixedRow(cost, "IDLE", ONE.subtract(worked).max(ZERO));
                affected(mapper.saveCost(row, category, person.salesPartId(), actor));
            }
        }
        affected(mapper.saveProfit(value.values(), actor));
    }
    public void unfix(String month) {
        definitions.definitions();
        mapper.unfixAssignments(month);
        mapper.unfixCosts(month);
        mapper.unfixProfit(month);
    }
    /** Idle or indirect staff line: no sales, team or project fees; only the labor cost is kept. */
    private static Cost fixedRow(Cost cost, String code, BigDecimal mm) {
        return new Cost(cost.yearMonth(), cost.developerId(), code, code, cost.projectName(), null, null,
            mm, ZERO, cost.labor(), ZERO, ZERO, null);
    }
    private static BigDecimal rate(List<Code> rows, String code) {
        return rows.stream().filter(row -> code.equals(row.code())).map(Code::value)
            .filter(Objects::nonNull).findFirst().orElse(ZERO);
    }
    private static void affected(int count) { if (count != 1) throw CodeFailure.conflict(); }
}
