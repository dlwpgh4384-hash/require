package com.company.common.billchecks.mapper;

import com.company.common.billchecks.dto.BillCheckContracts.Receipt;
import com.company.common.billchecks.mapper.BillCheckSources.*;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BillCheckMapper {
    String lastClosedMonth();
    boolean closed(String month);
    List<Monthly> monthly(MonthFilter filter);
    List<Invoice> invoices(String month);
    Invoice invoice(String id);
    List<Receipt> receipts(String id);
    BigDecimal normalReceipts(String id);
    String latestProjectBill(String projectId);
    /** 점검 월 단추: 그 해 취소 안 된 청구가 있는 달(until 달까지). */
    List<String> billMonths(@Param("year") String year, @Param("until") String until);
    void lockMonthlySources();
    void lockInvoices();

    int insert(Write write);
    int correct(Write write);
}
