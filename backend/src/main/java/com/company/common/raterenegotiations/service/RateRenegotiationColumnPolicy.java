package com.company.common.raterenegotiations.service;

import com.company.common.raterenegotiations.dto.RateRenegotiationContracts.Row;
import com.company.common.support.service.CodeFailure;
import java.util.HashSet;
import java.util.List;

public final class RateRenegotiationColumnPolicy {
    private RateRenegotiationColumnPolicy() {}
    public static final List<String> KEYS = List.of("developerName", "employeeTypeName", "projectName",
        "renewalDate", "monthlyMm", "currentUnitPrice", "monthlyCost", "profitRate", "targetUnitPrice",
        "requiredIncreaseRate", "annualProfitIncrease");
    private static final List<String> LABELS = List.of("이름", "구분", "프로젝트", "갱신일", "MM",
        "현재 단가", "월 원가", "이익률", "목표 단가", "필요 인상률", "n% 올리면 연 이익");
    public static List<String> validate(List<String> columns) {
        if (columns == null || columns.isEmpty() || new HashSet<>(columns).size() != columns.size()
            || columns.stream().anyMatch(c -> !KEYS.contains(c))) {
            throw CodeFailure.invalid("columns", "보이는 열을 중복 없이 선택해 주세요.");
        }
        return List.copyOf(columns);
    }
    public static String label(String key) { return LABELS.get(KEYS.indexOf(key)); }
    public static String value(Row row, String key) {
        Object value = switch (key) {
            case "developerName" -> row.developerName();
            case "employeeTypeName" -> row.employeeTypeName();
            case "projectName" -> row.projectName();
            case "renewalDate" -> row.renewalDate();
            case "monthlyMm" -> row.monthlyMm();
            case "currentUnitPrice" -> row.currentUnitPrice();
            case "monthlyCost" -> row.monthlyCost();
            case "profitRate" -> row.profitRate();
            case "targetUnitPrice" -> row.targetUnitPrice();
            case "requiredIncreaseRate" -> row.requiredIncreaseRate();
            case "annualProfitIncrease" -> row.annualProfitIncrease();
            default -> throw CodeFailure.invalid("columns", "등록된 열을 선택해 주세요.");
        };
        return value instanceof java.math.BigDecimal amount ? amount.toPlainString() : value.toString();
    }
}
