package com.company.common.profitstructures.service;

import com.company.common.profitstructures.dto.ProfitStructureContracts.*;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.util.*;

public final class ProfitStructureColumnPolicy {
    private ProfitStructureColumnPolicy() {}
    public static final Map<String, String> LABELS;
    public static final Set<String> COMPOSITION = Set.of("contractTypes", "topClients", "projectSalesParts");
    static {
        var labels = new LinkedHashMap<String, String>();
        labels.put("period", "기간"); labels.put("revenue", "매출"); labels.put("grossProfit", "매출총이익");
        labels.put("grossProfitRate", "매출총이익률"); labels.put("operatingProfit", "영업이익");
        labels.put("operatingProfitRate", "영업이익률"); labels.put("idleCostShare", "대기 원가 비중");
        labels.put("costOfSales", "매출원가"); labels.put("sellingAdministrativeExpense", "판관비");
        labels.put("previousAdjustment", "전월 조정"); labels.put("nonOperatingProfitLoss", "영업외손익");
        labels.put("monthlyCorporateTax", "법인세(월할)"); labels.put("netProfit", "당기순이익");
        labels.put("allocatedCost", "배분 원가"); labels.put("idleCost", "대기 원가"); labels.put("directExpense", "직접 경비");
        labels.put("referenceRevenue", "배분 기준 매출"); labels.put("contractTypes", "계약 유형별");
        labels.put("topClients", "고객사 상위 5곳(+그 밖)"); labels.put("projectSalesParts", "프로젝트 영업 파트별");
        LABELS = Collections.unmodifiableMap(labels);
    }
    public static List<String> validate(List<String> columns) {
        if (columns == null || columns.isEmpty() || columns.size() > LABELS.size()
            || columns.stream().anyMatch(key -> !LABELS.containsKey(key)) || new HashSet<>(columns).size() != columns.size())
            throw CodeFailure.invalid("columns", "숫자로 보기의 보이는 열을 선택해 주세요.");
        return List.copyOf(columns);
    }
    public static Object value(PeriodRow row, String key) {
        return switch (key) {
            case "period" -> row.period() + (row.provisional() ? " 잠정" : "");
            case "revenue" -> row.summary().revenue();
            case "grossProfit" -> row.summary().grossProfit();
            case "grossProfitRate" -> row.summary().grossProfitRate();
            case "operatingProfit" -> row.summary().operatingProfit();
            case "operatingProfitRate" -> row.summary().operatingProfitRate();
            case "idleCostShare" -> row.summary().idleCostShare();
            case "costOfSales" -> row.waterfall().costOfSales();
            case "sellingAdministrativeExpense" -> row.waterfall().sellingAdministrativeExpense();
            case "previousAdjustment" -> row.waterfall().previousAdjustment();
            case "nonOperatingProfitLoss" -> row.waterfall().nonOperatingProfitLoss();
            case "monthlyCorporateTax" -> row.waterfall().monthlyCorporateTax();
            case "netProfit" -> row.waterfall().netProfit();
            case "allocatedCost" -> row.trend().allocatedCost();
            case "idleCost" -> row.trend().idleCost();
            case "directExpense" -> row.trend().directExpense();
            case "referenceRevenue" -> row.trend().referenceRevenue();
            default -> null;
        };
    }
    public static List<CompositionRow> composition(PeriodRow row, String key) {
        if (row.composition() == null) return List.of();
        return switch (key) {
            case "contractTypes" -> row.composition().contractTypes();
            case "topClients" -> row.composition().topClients();
            case "projectSalesParts" -> row.composition().projectSalesParts();
            default -> List.of();
        };
    }
    public static String decimal(Object value) {
        return value == null ? "–" : value instanceof BigDecimal amount ? amount.toPlainString() : value.toString();
    }
}
