package com.company.common.candidates.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.CandidateDefinitions;
import com.company.common.candidates.mapper.CandidateMapper;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.YearMonth;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CandidateCalculationRules {
    /** 빨강 기준 = 운영 기준값 「견적 승인 기준 이익률」(식-87 예외·반올림, 2026-10-31 C27). */
    public static final String LOW_MARGIN_STANDARD = "QUOTE_APRV_RT";
    private final CandidateDefinitions definitions;
    private final CandidateMapper mapper;
    private final CalculationRuleService standards;
    public CandidateCalculationRules(CandidateDefinitions definitions, CandidateMapper mapper, CalculationRuleService standards) {
        this.definitions = definitions;
        this.mapper = mapper;
        this.standards = standards;
    }
    public record Terms(BigDecimal multiplier, BigDecimal overhead, BigDecimal lowMarginRate) {
        public BigDecimal profit(BigDecimal slot, BigDecimal desired) {
            return slot.subtract(desired).subtract(overhead).multiply(multiplier)
                .divide(slot, MathContext.DECIMAL128);
        }
        /** 기준보다 낮으면 빨강(기준과 같으면 빨강 아님). */
        public boolean lowMargin(BigDecimal profit) {
            return profit.compareTo(lowMarginRate) < 0;
        }
    }
    /** month: 그 달에 적용되는 운영 기준값(적용 시작월 ≤ month 중 가장 늦은 것)을 읽는다. */
    public Terms terms(boolean outsourcing, YearMonth month) {
        var definition = definitions.definitions().stream().filter(d -> "식-87".equals(d.calcId()))
            .findFirst().orElseThrow(() -> CodeFailure.invalid("calcId", "식-87 계산 정의를 확인해 주세요."));
        var multiplier = Pattern.compile("[×*]\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(definition.formula());
        if (!multiplier.find()) throw CodeFailure.invalid("calcId", "식-87 계산 정의를 확인해 주세요.");
        BigDecimal overhead = BigDecimal.ZERO;
        if (!outsourcing) {
            var team = mapper.allowance("TEAM_FEE");
            var expense = mapper.allowance("PEXP_FEE");
            if (team == null || expense == null) throw CodeFailure.invalid("calculation", "기본 팀비·프로젝트경비를 확인해 주세요.");
            overhead = team.add(expense);
        }
        var threshold = standards.effectiveStandard(LOW_MARGIN_STANDARD, month).value();
        if (threshold == null) throw CodeFailure.invalid("standardCode", "견적 승인 기준 이익률을 확인해 주세요.");
        return new Terms(new BigDecimal(multiplier.group(1)), overhead, threshold);
    }
}
