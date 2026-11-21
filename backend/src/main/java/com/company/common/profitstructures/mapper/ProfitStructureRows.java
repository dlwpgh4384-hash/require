package com.company.common.profitstructures.mapper;

import java.math.BigDecimal;
import java.time.Instant;

public final class ProfitStructureRows {
    private ProfitStructureRows() {}
    public record Query(int year, int baseMonth) {}
    public record Snapshot(String month, BigDecimal revenue, BigDecimal allocatedCost, BigDecimal idleCost,
        BigDecimal directExpense, BigDecimal costOfSales, BigDecimal grossProfit, BigDecimal sga,
        BigDecimal previousAdjustment, BigDecimal operatingProfit, BigDecimal income, BigDecimal expense,
        BigDecimal tax, BigDecimal netProfit, String migrated, String fixed, Instant modifiedAt,
        BigDecimal sellingAdministrativeInput, BigDecimal indirectLaborCost, BigDecimal internalResearchCost) {
        public Snapshot(String month, BigDecimal revenue, BigDecimal allocatedCost, BigDecimal idleCost,
            BigDecimal directExpense, BigDecimal costOfSales, BigDecimal grossProfit, BigDecimal sga,
            BigDecimal previousAdjustment, BigDecimal operatingProfit, BigDecimal income, BigDecimal expense,
            BigDecimal tax, BigDecimal netProfit, String migrated, String fixed, Instant modifiedAt) {
            this(month, revenue, allocatedCost, idleCost, directExpense, costOfSales, grossProfit, sga,
                previousAdjustment, operatingProfit, income, expense, tax, netProfit, migrated, fixed,
                modifiedAt, null, null, null);
        }
    }
    public record ProjectMonth(String month, String projectId, String contractType, String contractName,
        String clientId, String clientName, String partId, String partName, BigDecimal contractAmount,
        BigDecimal budget, BigDecimal billed, BigDecimal directExpense, BigDecimal distribution) {}
    public record StoredCost(String month, String projectId, String classification, BigDecimal revenue,
        BigDecimal labor, BigDecimal team, BigDecimal expense, BigDecimal directExpense) {}
    public record Amount(String month, String category, BigDecimal amount) {}
    public record PayrollClass(String month, String developerId, String classification) {}
}
