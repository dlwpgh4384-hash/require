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
    /** months: 점검 월 단추(올해 청구가 있는 달 중 이번 달까지 + 고른 달), lastClosedMonth: 마지막 마감 월(뒤 달은 「진행 중」). */
    public record CheckResult(String checkMonth, Summary summary, List<CheckRow> items,
        String calculationMethod, boolean plannedBasis, List<String> months, String lastClosedMonth) {}
    /**
     * supplyAmount: 창에서 고친 공급가(비우면 서버가 점검 값으로 채움).
     * modifiedAt: [청구 고치기] 때 화면이 읽은 고칠 청구의 수정 시각(00-1 동시 수정, 다르면 409).
     */
    public record Input(String projectId, String checkMonth, BigDecimal vatRate,
        LocalDate billDate, LocalDate payDueDate, String taxInvoiceNo, BigDecimal supplyAmount,
        Instant modifiedAt) {}
    public record Bill(String billId, String projectId, String projectName, String customerName,
        int billOrder, String billMonth, LocalDate billDate, BigDecimal supplyAmount,
        BigDecimal vatRate, BigDecimal vatAmount, BigDecimal totalAmount,
        BigDecimal receiptSumAmount, BigDecimal unpaidAmount, LocalDate payDueDate,
        String taxInvoiceNo, String status, boolean cancelled, String cancelReason,
        String cancelledBy, Instant cancelledAt, Integer dunningStep, int daysOverdue,
        boolean canEdit, boolean canCancel, boolean canReceive, boolean canCopy,
        boolean canNextMonth, Instant modifiedAt) {}
    public record Receipt(String receiptId, LocalDate receiptDate, BigDecimal amount,
        boolean cancelled, String cancelReason, String cancelledBy, Instant cancelledAt,
        boolean canCancel, Instant modifiedAt) {}
    public record Detail(Bill bill, List<Receipt> receipts, boolean missingTaxInvoiceNumber) {}
}
