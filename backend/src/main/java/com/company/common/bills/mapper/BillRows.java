package com.company.common.bills.mapper;

import com.company.common.bills.dto.BillContracts.Values;
import com.company.common.common.BillingCalculationRules.Amounts;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class BillRows {
    private BillRows() {}
    public record Invoice(String billId, String projectId, int billOrder, String billMonth,
        LocalDate billDate, BigDecimal supplyAmount, BigDecimal vatRate, BigDecimal vatAmount,
        BigDecimal totalAmount, BigDecimal receiptSumAmount, BigDecimal unpaidAmount,
        LocalDate payDueDate, String taxInvoiceNo, boolean cancelled, String cancelReason,
        String cancelledBy, Instant cancelledAt, String projectName, String customerName,
        String projectType, String projectStatus, LocalDate projectStart, LocalDate projectEnd,
        boolean latest, boolean closed, int normalReceiptCount) {}
    public record Project(String projectId, String projectType, String projectStatus,
        LocalDate startDate, LocalDate endDate) {}
    public record Write(String billId, String projectId, int billOrder, String billMonth,
        Values values, Amounts amounts, BigDecimal receipts, String actor) {}
    public record Cancellation(String billId, String reason, String actor) {}
    public record Recipient(String userId, String name, String email) {}
    public record RecipientFilter(String projectId, String month, int step) {}
    public record Notice(String billId, String userId, String content, String actor) {}
    public record Delivery(String billId, int step, LocalDate date, String recipients,
        boolean resend, String actor) {}
}
