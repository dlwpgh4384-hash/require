package com.company.common.raterenegotiations.service;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.RateRenegotiationDefinitions;
import com.company.common.personalcostprofits.dto.PersonalCostProfitContracts;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitMapper;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.personalcostprofits.service.PersonalCostProfitRules;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.raterenegotiations.dto.RateRenegotiationContracts.FormulaHelp;
import com.company.common.raterenegotiations.mapper.RateRenegotiationRows;
import com.company.common.roles.dto.RoleContracts.Role;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RateRenegotiationSource {
    private final PersonalCostProfitMapper mapper;
    private final CalculationRuleService calculation;
    private final RateRenegotiationDefinitions definitions;
    private final UserPreferencePeriodBasis periods;
    private final CurrentBusinessActor actor;
    private final com.company.common.raterenegotiations.mapper.RateRenegotiationMapper rateMapper;

    public RateRenegotiationSource(PersonalCostProfitMapper mapper, CalculationRuleService calculation,
        RateRenegotiationDefinitions definitions, UserPreferencePeriodBasis periods, CurrentBusinessActor actor,
        com.company.common.raterenegotiations.mapper.RateRenegotiationMapper rateMapper) {
        this.mapper = mapper;
        this.calculation = calculation;
        this.definitions = definitions;
        this.periods = periods;
        this.actor = actor;
        this.rateMapper = rateMapper;
    }
    public record Context(String yearMonth, boolean closed, BigDecimal targetRate, List<Cost> costs,
        List<Role> roles, FormulaHelp help, java.util.Set<String> salaryEstimated) {}

    public Context read() {
        var metadata = periods.metadata();
        if (!(metadata.get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis)) {
            throw CodeFailure.invalid("periodBasis", String.valueOf(metadata.get("periodBasisError")));
        }
        String ym = basis.latestYm();
        YearMonth month = YearMonth.parse(ym, DateTimeFormatter.ofPattern("uuuuMM"));
        var loaded = definitions.definitions();
        // UI-03 owns the established read-only payroll and assignment allocation source path.
        var request = new PersonalCostProfitContracts.Filter(month.getYear(), month.getMonthValue(),
            "월평균", null, null, null, null, null, "전체", true, null);
        boolean closed = ym.compareTo(basis.cashBaseYm()) < 0 || basis.closedMonths().contains(ym);
        List<Cost> costs;
        var estimated = new java.util.TreeSet<String>();
        if (closed) {
            costs = rateMapper.selectMonthlyCosts(ym);
        } else {
            var codes = mapper.selectCodes();
            var rates = new PersonalCostProfitRules.Rates(rate(codes, "ALLOW_TEAM"), rate(codes, "ALLOW_GROUP"),
                rate(codes, "SEV_RATE"), calculation.effectiveStandard("WTHLD_RT", month).value());
            var pay = mapper.selectPayroll(request);
            var assignments = mapper.selectAssignments(request);
            var leaves = mapper.selectLeaves(request);
            var pool = rateMapper.selectPoolSalaries(ym);
            costs = new ArrayList<>();
            for (var person : mapper.selectPeople(request)) {
                if (!PersonalCostProfitRules.employed(person, month)) continue;
                // 이 화면은 배분 원가만 쓴다. 턴키 매출(예산)은 쓰지 않으므로 예산이 비어도 멈추지 않게 계약금액으로 채운다.
                var inputs = assignments.stream().filter(a -> person.developerId().equals(a.developerId())
                    && ym.equals(a.yearMonth())).map(RateRenegotiationSource::withBudget).toList();
                var payroll = pay.stream().filter(p -> person.developerId().equals(p.developerId())
                    && ym.equals(p.yearMonth())).findFirst().orElse(null);
                if (payroll == null) {
                    // 시안 costOf: 급여 업로드 전이면 개발자 풀 월 급여로 계산한다(사용자 결정 2026-11-21, 이 화면만).
                    var salary = pool.stream().filter(s -> person.developerId().equals(s.developerId()))
                        .map(RateRenegotiationRows.PoolSalary::amount).findFirst().orElse(null);
                    if (salary != null) {
                        payroll = estimate(person, ym, salary, rate(codes, "INS_RATE"));
                        if (inputs.stream().anyMatch(a -> a.mm().signum() > 0)) estimated.add(person.developerId());
                    }
                }
                var personalLeaves = leaves.stream().filter(l -> person.developerId().equals(l.developerId())).toList();
                costs.addAll(PersonalCostProfitRules.live(person, payroll, inputs, ym, rates,
                    PersonalCostProfitRules.leaveAt(personalLeaves, month.atEndOfMonth())));
            }
        }
        return new Context(ym, closed, calculation.effectiveStandard("TGT_MRGN_RT", month).value(), costs,
            mapper.selectActorRoles(actor.userId()), help(loaded), java.util.Collections.unmodifiableSet(estimated));
    }
    private static Assignment withBudget(Assignment a) {
        if (!"TK".equals(a.contractTypeCode()) || a.distribution() != null
            || PersonalCostProfitRules.zero(a.budget()).signum() != 0) return a;
        return new Assignment(a.yearMonth(), a.developerId(), a.projectId(), a.assignmentId(), a.projectName(),
            a.jobName(), a.contractTypeCode(), a.inputTypeCode(), a.mm(), a.revenue(), a.team(), a.expense(),
            a.contractAmount(), a.contractAmount(), a.distribution());
    }
    /** 개발자 풀 월 급여로 만든 급여 줄. 수당·퇴직금은 공통 계산이 붙이고, 4대보험(회사)은 정규직·계약직만 INS_RATE로. */
    private static Pay estimate(Person person, String ym, BigDecimal salary, BigDecimal insuranceRate) {
        String type = person.employeeTypeCode();
        boolean employee = "FT".equals(type) || "CT".equals(type);
        BigDecimal insurance = employee
            ? PersonalCostProfitRules.money(salary.multiply(insuranceRate).movePointLeft(2)) : BigDecimal.ZERO;
        return new Pay(ym, person.developerId(), type, "OS".equals(type) ? BigDecimal.ZERO : salary,
            BigDecimal.ZERO, insurance, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, "OS".equals(type) ? salary : null);
    }
    private static BigDecimal rate(List<Code> codes, String key) {
        return codes.stream().filter(c -> "ALWNC".equals(c.groupCode()) && key.equals(c.code()) && c.value() != null)
            .map(Code::value).findFirst().orElseThrow(() -> CodeFailure.invalid("source", "수당 기준값을 확인해 주세요."));
    }
    private static String text(List<Definition> loaded, String id) {
        var d = loaded.stream().filter(v -> id.equals(v.calcId())).findFirst()
            .orElseThrow(() -> CodeFailure.invalid("source", "계산 정의를 확인해 주세요."));
        return d.formula() + "\n" + d.details();
    }
    private static FormulaHelp help(List<Definition> loaded) {
        String basis = text(loaded, "식-00");
        String target = text(loaded, "식-85");
        String annual = text(loaded, "식-90");
        String rounding = text(loaded, "식-57");
        // Q-식-85/86 are decisions within 식-90, not the unrelated 견적 승인 식-86.
        return new FormulaHelp(annual, basis + "\n" + annual, annual,
            annual + "\n" + target + "\n" + rounding, annual, annual + "\n" + rounding);
    }
}
