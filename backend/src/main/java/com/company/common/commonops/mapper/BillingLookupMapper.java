package com.company.common.commonops.mapper;

import com.company.common.common.BillingCalculationRules.MonthlyInput;
import com.company.common.commonops.dto.BillingLookupContracts.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BillingLookupMapper {
    record OptionFilter(String projectId, String month) {}
    record UnpaidBill(String billId, String projectId, String projectName, BigDecimal unpaidAmount,
        LocalDate payDueDate, boolean cancelled) {}
    record HistoryRow(String billId, int sequence, int step, LocalDate sentDate,
        String recipientDescription, String sendMethodCode, boolean resend) {
        public History value() { return new History(step, sentDate, recipientDescription, sendMethodCode, resend); }
    }
    List<ProjectOption> projects(OptionFilter filter);
    List<StatusOption> statuses();
    List<MonthlyInput> monthly(OptionFilter filter);
    List<UnpaidBill> overdue(@Param("today") LocalDate today);
    List<HistoryRow> history(@Param("today") LocalDate today);
    List<StatusOption> recipientRoles();
}
