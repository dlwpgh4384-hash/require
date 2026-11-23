package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Code;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CashFlowStatementDefinitions {
    private static final List<String> IDS = List.of(
        "식-00", "식-05", "식-06", "식-44", "식-45", "식-46", "식-47",
        "식-48", "식-49", "식-50", "식-51", "식-52");
    private static final Set<String> GROUPS = Set.of(
        "EMP_TYPE", "SGA_ITEM", "FIN_CLS", "DRCT_CLS", "PAY_TYPE", "IO", "XFER_CLS");
    private static final String PERCENT = "([0-9]+(?:\\.[0-9]+)?)";
    private static final Pattern VENDOR = Pattern.compile(
        "외주지급액=공급가액\\+부가세\\(" + PERCENT + "%\\)");
    private static final Pattern FREELANCER = Pattern.compile(
        "프리랜서지급액=공급가액−원천세\\(공급가액×" + PERCENT + "%\\)");
    private final CalculationRuleService metadata;
    private final ProfitStructureMapper source;

    public CashFlowStatementDefinitions(CalculationRuleService metadata, ProfitStructureMapper source) {
        this.metadata = metadata;
        this.source = source;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Definition> definitions() {
        return IDS.stream().map(id -> {
            var definition = metadata.definition(id);
            if (definition == null || !id.equals(definition.calcId())
                || compact(definition.formula()).isEmpty()) {
                throw CodeFailure.invalid("calcId", id + " 계산 정의를 확인해 주세요.");
            }
            return definition;
        }).toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Code> codes() {
        return source.codes().stream().filter(code -> GROUPS.contains(code.groupCode())).toList();
    }

    /** A per-request typed interpretation, never an evaluator or a cached copy of DB prose. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PaymentRules paymentRules() {
        var definitions = definitions();
        var common = definitions.get(0);
        var payment = definitions.stream().filter(row -> "식-49".equals(row.calcId()))
            .findFirst().orElseThrow();
        String principle = compact(common.details());
        String formula = compact(payment.formula());
        String[] clauses = formula.split("\\.(?=[가-힣])");
        if (!principle.contains("원단위정수저장")) {
            throw unsupported(common.calcId());
        }
        if (clauses.length != 4
            || !clauses[1].matches("프리랜서공급가액=그달급여(?:\\([^)]*\\))?\\+수당(?:\\([^)]*\\))?")
            || !clauses[3].equals("모두원단위버림")) {
            throw unsupported(payment.calcId());
        }
        var rules = new PaymentRules(percentage(VENDOR, clauses[0]), percentage(FREELANCER, clauses[2]));
        var groups = codes().stream().map(Code::groupCode).collect(java.util.stream.Collectors.toSet());
        if (!groups.containsAll(GROUPS)) {
            throw CodeFailure.invalid("code", "자금수지표 공통코드 그룹을 확인해 주세요.");
        }
        return rules;
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("(?U)\\s+", "");
    }

    private static BigDecimal percentage(Pattern pattern, String formula) {
        var match = pattern.matcher(formula);
        if (!match.matches()) throw unsupported("식-49");
        BigDecimal value = new BigDecimal(match.group(1));
        if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw unsupported("식-49");
        }
        return value.movePointLeft(2);
    }

    private static CodeFailure unsupported(String calcId) {
        return CodeFailure.invalid("calcId", calcId + " 지급액 정의의 계산 방식과 원 단위 처리를 확인해 주세요.");
    }

    /** Rates are fractions read from formula 49, not new employee/code enumerations. */
    public record PaymentRules(BigDecimal vendorVatRate, BigDecimal freelancerWithholdingRate) {
        public BigDecimal freelancerWithholding(BigDecimal salary, BigDecimal allowance) {
            return salary.add(allowance).multiply(freelancerWithholdingRate).setScale(0, RoundingMode.DOWN);
        }

        public BigDecimal freelancerPayment(BigDecimal salary, BigDecimal allowance) {
            return salary.add(allowance).subtract(freelancerWithholding(salary, allowance));
        }

        public BigDecimal vendorPayment(BigDecimal supplyAmount) {
            return supplyAmount.add(supplyAmount.multiply(vendorVatRate).setScale(0, RoundingMode.DOWN));
        }
    }
}
