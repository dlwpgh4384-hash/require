package com.company.common.personalcostprofits.service;

import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static java.math.BigDecimal.ZERO;
import static java.math.BigDecimal.ONE;

/** Semantic implementations of DB 식-01/02/08–17/23/53/55/57/95/96, not an expression interpreter. */
public final class PersonalCostProfitRules {
    private PersonalCostProfitRules() {}
    public record Rates(BigDecimal teamAllowance, BigDecimal groupAllowance,
        BigDecimal retirementPercent, BigDecimal withholdingPercent) {}
    public record Month(List<Cost> costs, Map<String, BigDecimal> payroll, String employeeTypeCode) {}

    public static BigDecimal zero(BigDecimal value) { return value == null ? ZERO : value; }
    public static BigDecimal money(BigDecimal value) { return value.setScale(0, RoundingMode.DOWN); }
    public static BigDecimal divide(BigDecimal value, BigDecimal denominator, int scale) {
        return denominator.signum() == 0 ? ZERO : value.divide(denominator, scale, RoundingMode.HALF_UP);
    }
    public static BigDecimal percent(BigDecimal value, BigDecimal denominator) {
        return divide(value.multiply(BigDecimal.valueOf(100)), denominator, 1);
    }
    public static boolean employed(Person person, YearMonth month) {
        return (person.joinDate() == null || !YearMonth.from(person.joinDate()).isAfter(month))
            && (person.retireDate() == null || !YearMonth.from(person.retireDate()).isBefore(month));
    }
    public static boolean leaveAt(List<Leave> leaves, java.time.LocalDate day) {
        return leaves.stream().anyMatch(row -> !row.startDate().isAfter(day)
            && (row.endDate() == null || !row.endDate().isBefore(day)));
    }
    public static Map<String, BigDecimal> payroll(Person person, Pay pay, Rates rates, boolean closed) {
        Map<String, BigDecimal> values = new HashMap<>();
        String type = pay == null ? person.employeeTypeCode() : pay.employeeTypeCode();
        BigDecimal salary = pay == null ? ZERO : zero(pay.salary());
        BigDecimal allowance = ZERO;
        BigDecimal retirement = ZERO;
        if (pay != null) {
            allowance = closed ? zero(pay.allowance()) : ("FT".equals(type) || "CT".equals(type))
                ? "TEAM".equals(person.positionCode()) ? rates.teamAllowance()
                : "GROUP".equals(person.positionCode()) ? rates.groupAllowance() : ZERO : ZERO;
            retirement = closed ? zero(pay.retirementReserve()) : "FT".equals(type)
                ? money(salary.add(allowance).multiply(rates.retirementPercent()).movePointLeft(2)) : ZERO;
        }
        BigDecimal insurance = pay == null ? ZERO : zero(pay.companyInsurance());
        BigDecimal self = pay == null ? ZERO : zero(pay.personalInsurance());
        BigDecimal incomeTax = pay == null ? ZERO : zero(pay.incomeTax());
        BigDecimal withholding = pay == null ? ZERO : closed ? zero(pay.withholdingTax())
            : ("CT".equals(type) || "FL".equals(type))
                ? money(salary.add(allowance).multiply(rates.withholdingPercent()).movePointLeft(2)) : ZERO;
        BigDecimal labor = "OS".equals(type) ? pay == null ? ZERO : zero(pay.vendorSupply())
            : "FL".equals(type) ? salary : salary.add(allowance).add(insurance).add(retirement);
        values.put("monthlySalary", salary);
        values.put("allowance", allowance);
        values.put("companyInsurance", insurance);
        values.put("retirementReserve", retirement);
        values.put("personalInsurance", self);
        values.put("withholdingTax", withholding);
        values.put("incomeTax", incomeTax);
        values.put("netPay", salary.add(allowance).subtract(self).subtract(withholding).subtract(incomeTax));
        values.put("personalCost", labor);
        return values;
    }

    public static List<Cost> live(Person person, Pay pay, List<Assignment> inputs,
        String month, Rates rates, boolean leave) {
        var amounts = payroll(person, pay, rates, false);
        BigDecimal labor = amounts.get("personalCost");
        String type = pay == null ? person.employeeTypeCode() : pay.employeeTypeCode();
        if (leave) return pay == null ? new ArrayList<>() : new ArrayList<>(List.of(idle(person, month, labor)));
        var sorted = inputs.stream().filter(a -> a.mm().signum() > 0)
            .sorted(Comparator.comparing(Assignment::projectId).thenComparing(Assignment::assignmentId)).toList();
        BigDecimal mm = sorted.stream().map(Assignment::mm).reduce(ZERO, BigDecimal::add);
        boolean external = "FL".equals(type) || "OS".equals(type);
        BigDecimal denominator = external ? mm : mm.max(ONE);
        BigDecimal idle = external || mm.compareTo(ONE) >= 0 ? ZERO : money(labor.multiply(ONE.subtract(mm)));
        BigDecimal target = labor.subtract(idle);
        BigDecimal allocated = ZERO;
        List<Cost> result = new ArrayList<>();
        Map<String, BigDecimal> projectMm = new HashMap<>();
        Map<String, BigDecimal> distributionUsed = new HashMap<>();
        for (var row : sorted) if (!"FREE".equals(row.inputTypeCode())) projectMm.merge(row.projectId(), row.mm(), BigDecimal::add);
        for (int i = 0; i < sorted.size(); i++) {
            var a = sorted.get(i);
            BigDecimal cost = i == sorted.size() - 1 ? target.subtract(allocated)
                : money(divide(labor.multiply(a.mm()), denominator, 12));
            allocated = allocated.add(cost);
            BigDecimal revenue = zero(a.revenue());
            if ("IN".equals(a.contractTypeCode()) || "FREE".equals(a.inputTypeCode())) revenue = ZERO;
            else if ("TK".equals(a.contractTypeCode())) {
                if (a.distribution() != null) {
                    boolean last = sorted.subList(i + 1, sorted.size()).stream().noneMatch(next ->
                        next.projectId().equals(a.projectId()) && !"FREE".equals(next.inputTypeCode()));
                    revenue = last ? a.distribution().subtract(distributionUsed.getOrDefault(a.projectId(), ZERO))
                        : money(divide(a.distribution().multiply(a.mm()), projectMm.get(a.projectId()), 12));
                    distributionUsed.merge(a.projectId(), revenue, BigDecimal::add);
                } else {
                    if (zero(a.budget()).signum() == 0 && zero(a.contractAmount()).signum() != 0) {
                        throw CodeFailure.invalid("source", "턴키 프로젝트 예산을 확인해 주세요.");
                    }
                    revenue = money(divide(cost.multiply(zero(a.contractAmount())), zero(a.budget()), 12));
                }
            }
            boolean feesExcluded = "OS".equals(type) || "INDRCT".equals(person.directClassCode());
            BigDecimal team = feesExcluded ? ZERO : money(zero(a.team()).multiply(a.mm()));
            BigDecimal expense = feesExcluded ? ZERO : money(zero(a.expense()).multiply(a.mm()));
            result.add(new Cost(month, person.developerId(), a.projectId(), a.assignmentId(), a.projectName(),
                a.jobName(), a.contractTypeCode(), a.mm(), revenue, cost, team, expense, a.inputTypeCode()));
        }
        if (idle.signum() != 0 || (!external && sorted.isEmpty() && pay != null)) {
            result.add(idle(person, month, sorted.isEmpty() ? labor : idle));
        }
        return result;
    }
    public static Cost idle(Person person, String month, BigDecimal labor) {
        return new Cost(month, person.developerId(), "IDLE", "IDLE", "남는 몫(대기 원가)", null,
            null, ZERO, ZERO, labor, ZERO, ZERO, null);
    }
}
