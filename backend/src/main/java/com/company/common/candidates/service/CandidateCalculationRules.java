package com.company.common.candidates.service;

import com.company.common.common.CandidateDefinitions;
import com.company.common.candidates.mapper.CandidateMapper;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CandidateCalculationRules {
    private final CandidateDefinitions definitions;
    private final CandidateMapper mapper;
    public CandidateCalculationRules(CandidateDefinitions definitions, CandidateMapper mapper) {
        this.definitions = definitions;
        this.mapper = mapper;
    }
    public record Terms(BigDecimal multiplier, BigDecimal overhead) {
        public BigDecimal profit(BigDecimal slot, BigDecimal desired) {
            return slot.subtract(desired).subtract(overhead).multiply(multiplier)
                .divide(slot, MathContext.DECIMAL128);
        }
    }
    public Terms terms(boolean outsourcing) {
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
        return new Terms(new BigDecimal(multiplier.group(1)), overhead);
    }
    public static boolean lowMargin(BigDecimal profit) {
        return profit.compareTo(BigDecimal.valueOf(15)) < 0;
    }
}
