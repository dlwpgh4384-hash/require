package com.company.common.billchecks.mapper;

import com.company.common.billchecks.dto.BillCheckContracts.Input;
import com.company.common.common.BillingCalculationRules.Amounts;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class BillCheckSources {
    private BillCheckSources() {}

    public record MonthFilter(String month, boolean closed) {}
    public record Monthly(String projectId, String projectName, String projectType,
        String developerId, String developerName, LocalDate inputStart, String inputType,
        BigDecimal monthlyMm, BigDecimal billingPrice, BigDecimal salesAmount) {}
    public record Write(String billId, int billOrder, Input input, Amounts amounts,
        BigDecimal receipts, String actor) {}

    /** Physical invoice projection, explicitly mapped; response capabilities are service-owned. */
    public static class Invoice {
        public String billId;
        public String projectId;
        public String projectName;
        public String customerName;
        public String projectType;
        public String projectStatus;
        public LocalDate projectStart;
        public LocalDate projectEnd;
        public int billOrder;
        public String billMonth;
        public LocalDate billDate;
        public BigDecimal supplyAmount;
        public BigDecimal vatRate;
        public BigDecimal vatAmount;
        public BigDecimal totalAmount;
        public BigDecimal receiptSumAmount;
        public BigDecimal unpaidAmount;
        public LocalDate payDueDate;
        public String taxInvoiceNo;
        public boolean cancelled;
        public String cancelReason;
        public String cancelledBy;
        public Instant cancelledAt;
        public Instant modifiedAt;
    }
}
