package com.company.common.cashflowstatements.mapper;

import com.company.common.financeitems.dto.FinanceItemContracts.CashContribution;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Pay;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CashFlowStatementMapper {
    String currentMonth();
    BigDecimal immediateStandard(@Param("code") String code);
    List<CashContribution> receipts();
    List<CashContribution> miscellaneousIncome();
    List<Pay> payroll(@Param("end") String end);
    List<String> activeDevelopers();
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
}
