package com.company.common.netprofitstatements.dto;

import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts.NumericTable;
import java.math.BigDecimal;
import java.util.List;

public final class NetProfitStatementContracts {
    private NetProfitStatementContracts() {}
    public record Filter(Integer year, String baseMonth) {}
    public record Summary(BigDecimal cumulativeNetProfit, BigDecimal netProfitRate,
        BigDecimal baseMonthNetProfit, BigDecimal previousMonthChangePercent) {}
    public record Contribution(String contractTypeCode, String contractTypeName,
        BigDecimal amount, BigDecimal sharePercent) {}
    public record Result(int year, String baseMonth, boolean provisional, String provisionalNotice,
        Summary summary, NumericTable numericTable, boolean contributionVisible,
        List<Contribution> contributions) {}
}
