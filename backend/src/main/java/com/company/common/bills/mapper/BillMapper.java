package com.company.common.bills.mapper;

import com.company.common.bills.dto.BillContracts.Receipt;
import com.company.common.bills.mapper.BillRows.*;
import com.company.common.common.BillingCalculationRules.MonthlyInput;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BillMapper {
    List<Invoice> yearRows(int year);
    Invoice invoice(String id);
    Invoice lockInvoice(String id);
    Project project(String id);
    List<Receipt> receipts(String id);
    BigDecimal normalReceipts(String id);
    boolean closed(String month);
    boolean exists(@Param("projectId") String projectId, @Param("month") String month);
    void lockMonthlySources();
    List<MonthlyInput> monthly(@Param("projectId") String projectId, @Param("month") String month);
    int insert(Write write);
    int update(Write write);
    int cancel(Cancellation cancellation);
    List<Recipient> recipients(RecipientFilter filter);
    boolean resent(@Param("id") String id, @Param("step") int step);
    boolean automaticallySent(@Param("id") String id, @Param("step") int step);
    List<String> outstandingIds();
    int notification(Notice notice);
    int history(Delivery delivery);
}
