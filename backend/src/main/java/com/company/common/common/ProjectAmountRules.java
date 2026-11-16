package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Project-only numeric adapters. Definitions are read afresh; no text is executed. */
@Component
public class ProjectAmountRules {
    private static final List<String> DEFINITION_IDS = List.of("식-00", "식-04", "식-11", "식-16", "식-18",
        "식-19", "식-20", "식-24", "식-25", "식-53", "식-54", "식-55", "식-56", "식-67", "식-72", "식-73", "식-76");
    private static final Pattern BUDGET_FACTOR = Pattern.compile(
        "(?:현재\\s*)?계약금액\\s*[×*]\\s*\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([−-])\\s*목표\\s*마진율\\s*\\)(?=\\s*(?:[,，).]|$))");
    private final CalculationRuleService metadata;

    public ProjectAmountRules(CalculationRuleService metadata) { this.metadata = metadata; }

    public List<Definition> definitions() {
        return DEFINITION_IDS.stream().map(metadata::definition).toList();
    }

    /** Call only for automatic, non-internal budgets; manual budget ownership stays with the command. */
    public BigDecimal automaticBudget(BigDecimal contractAmount, YearMonth month) {
        nonnegative(contractAmount, "contractAmount");
        metadata.definition("식-00");
        var definition = metadata.definition("식-19");
        var factor = BUDGET_FACTOR.matcher(definition.formula());
        if (!factor.find()) throw unsupported("식-19");
        BigDecimal base = new BigDecimal(factor.group(1));
        BigDecimal margin = metadata.effectiveStandard("TGT_MRGN_RT", month).value();
        if (margin == null || margin.signum() < 0 || margin.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException("TGT_MRGN_RT 적용 값을 확인해 주세요.");
        }
        BigDecimal multiplier = base.subtract(margin.movePointLeft(2));
        if (multiplier.signum() < 0) throw unsupported("식-19");
        return contractAmount.multiply(multiplier).setScale(0, RoundingMode.DOWN);
    }

    public PersonnelDirectCost personnelDirectCost(String employeeTypeCode, BigDecimal monthlyTeamAmount,
            BigDecimal monthlyProjectAmount, BigDecimal projectMm) {
        requireDirectCostDefinition();
        nonnegative(monthlyTeamAmount, "monthlyTeamAmount");
        nonnegative(monthlyProjectAmount, "monthlyProjectAmount");
        nonnegative(projectMm, "projectMm");
        if ("OS".equals(employeeTypeCode)) return new PersonnelDirectCost(BigDecimal.ZERO, BigDecimal.ZERO);
        if (!List.of("FT", "CT", "FL").contains(employeeTypeCode)) {
            throw new IllegalArgumentException("EMP_TYPE 값을 확인해 주세요.");
        }
        return new PersonnelDirectCost(monthlyTeamAmount.multiply(projectMm).setScale(0, RoundingMode.DOWN),
            monthlyProjectAmount.multiply(projectMm).setScale(0, RoundingMode.DOWN));
    }

    /** Project-only aggregation: additional expenses never change a personnel cost or payment row. */
    public BigDecimal projectDirectCost(BigDecimal personnelDirectCost, BigDecimal additionalExpenses) {
        requireDirectCostDefinition();
        nonnegative(personnelDirectCost, "personnelDirectCost");
        nonnegative(additionalExpenses, "additionalExpenses");
        return personnelDirectCost.add(additionalExpenses);
    }

    private void requireDirectCostDefinition() {
        metadata.definition("식-00");
        var definition = metadata.definition("식-11");
        // Historical allocation prose in details is superseded by the explicit per-person rule.
        String[] expressions = definition.formula().split("\\.\\s*");
        if (expressions.length != 2) throw unsupported("식-11");
        String person = expressions[0].replaceAll("\\s+", "")
            .replace("팀비·경비몫=", "")
            .replace("그프로젝트팀비(월)", "TEAM")
            .replace("프로젝트경비(월)", "PROJECT")
            .replace("그사람의그프로젝트MM", "MM").replace('×', '*');
        String project = expressions[1].replaceAll("\\s+", "")
            .replace("프로젝트직접경비=", "")
            .replace("투입자사인력팀비·경비몫의합", "PERSONNEL")
            .replace("그달추가직접경비합계", "ADDITIONAL");
        if (!person.equals("(TEAM+PROJECT)*MM") || !project.equals("PERSONNEL+ADDITIONAL")) {
            throw unsupported("식-11");
        }
    }

    private static void nonnegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(field + " 값을 확인해 주세요.");
    }

    private static IllegalStateException unsupported(String calcId) {
        return new IllegalStateException("지원하지 않는 " + calcId + " 정의입니다. DB 계산식의 지원 연산을 확인해 주세요.");
    }

    public record PersonnelDirectCost(BigDecimal teamAmount, BigDecimal projectAmount) {
        public BigDecimal total() { return teamAmount.add(projectAmount); }
    }
}
