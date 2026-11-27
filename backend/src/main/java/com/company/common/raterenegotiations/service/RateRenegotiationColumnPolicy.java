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
        "현재 단가", "월 원가(기준월)", "이익률", "목표 단가", "필요 인상률", "올리면 연 이익");
    /** 민감 체크가 없으면 빼는 열(현재 단가 = 월 단가, 월 원가 = 개인별 원가, 목표 단가 = 월 원가로 셀 수 있는 금액). */
    public static final java.util.Set<String> SENSITIVE = java.util.Set.of("currentUnitPrice", "monthlyCost", "targetUnitPrice");
    public static List<String> validate(List<String> columns) {
        if (columns == null || columns.isEmpty() || new HashSet<>(columns).size() != columns.size()
            || columns.stream().anyMatch(c -> !KEYS.contains(c))) {
            throw CodeFailure.invalid("columns", "보이는 열을 중복 없이 선택해 주세요.");
        }
        // 민감 체크가 없으면 현재 단가·월 원가·목표 단가 열을 엑셀에서 뺀다(명세 ⑨-2, 응답 칸과 같게).
        if (!com.company.common.support.auth.RequestAccess.sensitiveVisible())
            return columns.stream().filter(column -> !SENSITIVE.contains(column)).toList();
        return List.copyOf(columns);
    }
    public static String label(String key) { return LABELS.get(KEYS.indexOf(key)); }
    /** 화면 열 이름과 같게: 목표 단가 (목표 이익률%), 인상률% 올리면 연 이익. */
    public static String label(String key, java.math.BigDecimal increaseRate, java.math.BigDecimal targetRate) {
        return switch (key) {
            case "targetUnitPrice" -> "목표 단가 (" + plain(targetRate) + "%)";
            case "annualProfitIncrease" -> plain(increaseRate) + "% 올리면 연 이익";
            default -> label(key);
        };
    }
    private static String plain(java.math.BigDecimal value) {
        return value == null ? "–" : value.stripTrailingZeros().toPlainString();
    }
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
