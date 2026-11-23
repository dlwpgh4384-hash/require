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
    /** 개발자 풀 월 급여·지급액(그 달에 맞는 이력). 급여를 한 번도 올리지 않은 사람의 예상 급여에 쓴다. */
    List<PoolPay> poolPays(@Param("month") String month);
    record PoolPay(String developerId, BigDecimal amount) {}
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
}
