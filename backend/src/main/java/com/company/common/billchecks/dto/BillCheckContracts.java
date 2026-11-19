package com.company.common.billchecks.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BillCheckContracts {
    private BillCheckContracts() {}

    public record CheckRow(String projectId, String projectName, String projectType,
        String judgement, int headcount, BigDecimal monthlyMm, BigDecimal referenceAmount,
        BigDecimal billedSupplyAmount, BigDecimal difference, String explanation,
        String latestBillId, String allowedAction) {}
    public record Summary(int totalCount, int missingCount, int underCount, int overCount,
        int matchedCount) {}
    public record CheckResult(String checkMonth, Summary summary, List<CheckRow> items,
        String calculationMethod, boolean plannedBasis) {}
    public record Input(String projectId, String checkMonth, BigDecimal vatRate,
        LocalDate billDate, LocalDate payDueDate, String taxInvoiceNo) {}
    public record Bill(String billId, String projectId, String projectName, String customerName,
        int billOrder, String billMonth, LocalDate billDate, BigDecimal supplyAmount,
        BigDecimal vatRate, BigDecimal vatAmount, BigDecimal totalAmount,
        BigDecimal receiptSumAmount, BigDecimal unpaidAmount, LocalDate payDueDate,
        String taxInvoiceNo, String status, boolean cancelled, String cancelReason,
        String cancelledBy, Instant cancelledAt, Integer dunningStep, int daysOverdue,
        boolean canEdit, boolean canCancel, boolean canReceive, boolean canCopy,
        boolean canNextMonth) {}
    public record Receipt(String receiptId, LocalDate receiptDate, BigDecimal amount,
        boolean cancelled, String cancelReason, String cancelledBy, Instant cancelledAt,
        boolean canCancel) {}
    public record Detail(Bill bill, List<Receipt> receipts, boolean missingTaxInvoiceNumber) {}
}
