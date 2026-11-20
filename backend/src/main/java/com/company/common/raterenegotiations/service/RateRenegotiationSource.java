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
        List<Role> roles, FormulaHelp help) {}

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
        if (closed) {
            costs = rateMapper.selectMonthlyCosts(ym);
        } else {
            var codes = mapper.selectCodes();
            var rates = new PersonalCostProfitRules.Rates(rate(codes, "ALLOW_TEAM"), rate(codes, "ALLOW_GROUP"),
                rate(codes, "SEV_RATE"), calculation.effectiveStandard("WTHLD_RT", month).value());
            var pay = mapper.selectPayroll(request);
            var assignments = mapper.selectAssignments(request);
            var leaves = mapper.selectLeaves(request);
            costs = new ArrayList<>();
            for (var person : mapper.selectPeople(request)) {
                if (!PersonalCostProfitRules.employed(person, month)) continue;
                var inputs = assignments.stream().filter(a -> person.developerId().equals(a.developerId())
                    && ym.equals(a.yearMonth())).toList();
                var payroll = pay.stream().filter(p -> person.developerId().equals(p.developerId())
                    && ym.equals(p.yearMonth())).findFirst().orElse(null);
                var personalLeaves = leaves.stream().filter(l -> person.developerId().equals(l.developerId())).toList();
                costs.addAll(PersonalCostProfitRules.live(person, payroll, inputs, ym, rates,
                    PersonalCostProfitRules.leaveAt(personalLeaves, month.atEndOfMonth())));
            }
        }
        return new Context(ym, closed, calculation.effectiveStandard("TGT_MRGN_RT", month).value(), costs,
            mapper.selectActorRoles(actor.userId()), help(loaded));
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
