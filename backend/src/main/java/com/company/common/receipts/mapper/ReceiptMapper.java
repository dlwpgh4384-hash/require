package com.company.common.receipts.mapper;

import com.company.common.receipts.dto.ReceiptContracts.Receipt;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReceiptMapper {
    record Bill(String billId, BigDecimal totalAmount, boolean cancelled) {}
    record ReceiptRow(String receiptId, String billId, LocalDate receiptDate,
        BigDecimal amount, boolean cancelled, String cancelReason, String cancelledBy,
        Instant cancelledAt, boolean canCancel, Instant modifiedAt) {
        public Receipt view() {
            return new Receipt(receiptId, receiptDate, amount, cancelled, cancelReason,
                cancelledBy, cancelledAt, canCancel, modifiedAt);
        }
    }
    record ReceiptWrite(String billId, LocalDate receiptDate, BigDecimal amount, String actor) {}
    record BalanceWrite(String billId, BigDecimal receiptSumAmount,
        BigDecimal unpaidAmount, String actor) {}

    Bill lockBill(@Param("billId") String billId);
    boolean closed(@Param("month") String month);
    BigDecimal normalSum(@Param("billId") String billId);
    ReceiptRow receipt(@Param("receiptId") String receiptId);
    ReceiptRow lockReceipt(@Param("receiptId") String receiptId);
    String insertReceipt(ReceiptWrite write);
    int cancel(@Param("receiptId") String receiptId, @Param("reason") String reason,
        @Param("actor") String actor, @Param("modifiedAt") Instant modifiedAt);
    int updateBalance(BalanceWrite write);
}
