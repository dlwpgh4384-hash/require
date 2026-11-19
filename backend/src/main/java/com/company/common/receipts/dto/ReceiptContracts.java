package com.company.common.receipts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReceiptContracts {
    private ReceiptContracts() {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Receipt(String receiptId, LocalDate receiptDate, BigDecimal amount,
        boolean cancelled, String cancelReason, String cancelledBy, Instant cancelledAt,
        boolean canCancel, Instant modifiedAt) {}

    public record Result(List<String> billIds, List<Receipt> receipts) {}
    public record Item(String billId, BigDecimal amount) {}
}
