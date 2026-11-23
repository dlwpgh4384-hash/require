package com.company.common.bills.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class BillContracts {
    private BillContracts() {}
    public record Filter(Integer year, String status, String billMonth, String projectId) {}
    public record Bill(String billId, String projectId, String projectName, String customerName,
        int billOrder, String billMonth, LocalDate billDate, BigDecimal supplyAmount,
        BigDecimal vatRate, BigDecimal vatAmount, BigDecimal totalAmount, BigDecimal receiptSumAmount,
        BigDecimal unpaidAmount, LocalDate payDueDate, String taxInvoiceNo, String status,
        boolean cancelled, String cancelReason, String cancelledBy, Instant cancelledAt,
        Integer dunningStep, int daysOverdue, boolean canEdit, boolean canCancel,
        boolean canReceive, boolean canCopy, boolean canNextMonth, Instant modifiedAt) {}
    public record Receipt(String receiptId, LocalDate receiptDate, BigDecimal amount,
        boolean cancelled, String cancelReason, String cancelledBy, Instant cancelledAt,
        boolean canCancel, Instant modifiedAt) {}
    public record Detail(Bill bill, List<Receipt> receipts, boolean missingTaxInvoiceNumber) {}
    public record Summary(int yearBillCount, BigDecimal yearBillTotal, BigDecimal currentMonthBillTotal,
        int unpaidCount, BigDecimal unpaidTotal, int overdueCount) {}
    public record Totals(BigDecimal supplyAmount, BigDecimal vatAmount, BigDecimal totalAmount,
        BigDecimal receiptSumAmount, BigDecimal unpaidAmount) {}
    public record BillList(List<Bill> items, Summary summary, Totals totals, boolean readOnly) {}
    public record SaveResponse(boolean success, Map<String,Object> meta, Detail data, String warning) {
        public static SaveResponse saved(Detail detail) { return new SaveResponse(true, Map.of(), detail, null); }
        public static SaveResponse warning(String text) { return new SaveResponse(true, Map.of(), null, text); }
    }
    public record Values(BigDecimal supplyAmount, BigDecimal vatRate, LocalDate billDate,
        LocalDate payDueDate, String taxInvoiceNo) {}
    /** 수정·취소 요청: 화면이 읽은 수정 시각(modifiedAt)이 지금 값과 다르면 409. */
    public record EditInput(Values values, Instant modifiedAt) {}
    public record CancelInput(String reason, Instant modifiedAt) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
    public record CreateInput(String projectId, String billMonth, Values values,
        String sourceBillId, boolean duplicateConfirmed) {}
    public record DunningHistory(int step, LocalDate sentDate, String recipientDescription,
        String sendMethodCode, boolean resend) {}
    public record SendResult(String billId, DunningHistory history) {}
    /** 메뉴 배지: 미수가 남은 청구 건수(SFR-001-04). */
    public record UnpaidCount(int count) {}
}
