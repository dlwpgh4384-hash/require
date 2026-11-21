package com.company.common.operatingprofitstatements.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperatingProfitStatementMapper {
    void lockSources();
    int upsertImported(ImportedMonth row);
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
    final class ImportedMonth {
        public String month, userId;
        public BigDecimal revenue, allocatedCost, idleCost, directExpense, costOfSales, grossProfit;
        public BigDecimal sellingAdministrativeInput, indirectLaborCost, internalResearchCost, sga;
        public BigDecimal previousAdjustment, operatingProfit, income, expense, tax, netProfit;
    }
}
