package com.company.common.projectcostanalyses.service;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisRows.*;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import static java.math.BigDecimal.ZERO;
import static java.math.BigDecimal.ONE;

/** Typed semantic implementations; metadata remains DB-owned, never executable expression text. */
public final class ProjectCostAnalysisRules {
    private final Map<String, Definition> definitions;
    public ProjectCostAnalysisRules(List<Definition> loaded) {
        definitions = loaded.stream().collect(Collectors.toMap(Definition::calcId, Function.identity()));
        Map.ofEntries(Map.entry("식-00", ""), Map.entry("식-18", "진행률"),
            Map.entry("식-19", "예산"), Map.entry("식-20", "MM"), Map.entry("식-21", "마진율"),
            Map.entry("식-22", "매출"), Map.entry("식-24", "MM"), Map.entry("식-25", "배분"),
            Map.entry("식-26", "원가"), Map.entry("식-27", "무상지원"), Map.entry("식-91", "투입 초과"))
            .forEach((id, token) -> {
                var rule = definitions.get(id);
                if (rule == null || rule.formula() == null || rule.formula().isBlank()
                    || !rule.formula().contains(token)) {
                    throw CodeFailure.invalid("source", "계산 정의와 분석 규칙을 확인해 주세요.");
                }
            });
    }
    public String basis(String id) { return definitions.get(id).formula(); }
    public static BigDecimal ratio(BigDecimal value, BigDecimal denominator) {
        return value == null || denominator == null || denominator.signum() <= 0 ? null
            : value.divide(denominator, 16, RoundingMode.HALF_UP);
    }
    public static BigDecimal rate(BigDecimal revenue, BigDecimal cost) {
        var quotient = ratio(revenue == null || cost == null ? null : revenue.subtract(cost), revenue);
        return quotient == null ? null : quotient.movePointRight(2);
    }
    public BigDecimal revenue(Project project, BigDecimal cost) {
        if (!"TK".equals(project.contractTypeCode())) throw new IllegalArgumentException();
        if (project.contractAmount() == null) return null;
        var progress = "END".equals(project.statusCode()) ? ONE : ratio(cost, project.budget());
        return progress == null ? null : project.contractAmount().multiply(progress.min(ONE));
    }
    public static boolean lowMargin(BigDecimal margin, BigDecimal target) {
        return margin != null && target.subtract(margin).compareTo(BigDecimal.valueOf(5)) > 0;
    }
    public static String budgetColor(BigDecimal usage) {
        if (usage == null || usage.compareTo(BigDecimal.valueOf(80)) < 0) return null;
        return usage.compareTo(BigDecimal.valueOf(100)) >= 0 ? "빨강" : "주황";
    }
    public static String history(Project p, List<History> history, String code, LocalDate cutoff, String current) {
        var rows = history.stream().filter(h -> h.projectId().equals(p.projectId()) && h.itemCode().equals(code)).toList();
        // First later change's old value is the state immediately before that change.
        var later = rows.stream().filter(h -> h.changeDate().isAfter(cutoff)).findFirst();
        if (later.isPresent()) return later.get().beforeValue();
        var past = rows.stream().filter(h -> !h.changeDate().isAfter(cutoff)).reduce((a,b) -> b);
        return past.map(History::afterValue).orElse(current);
    }
    public static BigDecimal historicAmount(Project p, List<History> history, String code,
        LocalDate cutoff, BigDecimal current) {
        boolean evidenced = history.stream().anyMatch(h -> h.projectId().equals(p.projectId()) && h.itemCode().equals(code));
        if (!evidenced && p.modifiedDate() != null && p.modifiedDate().isAfter(cutoff)) return null;
        String text = history(p, history, code, cutoff, current == null ? null : current.toPlainString());
        if (text == null || text.isBlank()) return null;
        try { return new BigDecimal(text); }
        catch (NumberFormatException failure) { return null; }
    }
    public static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(java.util.Objects::nonNull).reduce(ZERO, BigDecimal::add);
    }
}
