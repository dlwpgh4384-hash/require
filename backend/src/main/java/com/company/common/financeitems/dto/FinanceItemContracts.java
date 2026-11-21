package com.company.common.financeitems.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FinanceItemContracts {
    private FinanceItemContracts() {}
    public record Item(String itemId, Instant modifiedAt, String classification, String itemCode,
        String itemName, BigDecimal amount, BigDecimal vatAmount, LocalDate cashDate,
        String repeatYn, String autoFillYn, String uploadId, String memo) {}
    public record Loan(String loanId, Instant modifiedAt, LocalDate date, String direction,
        BigDecimal amount, String counterparty, String memo) {}
    public record Adjustment(String adjustmentId, Instant modifiedAt, String targetYearMonth,
        String adjustmentItem, BigDecimal amount, String reason) {}
    public record Transfer(String transferId, Instant modifiedAt, LocalDate date, String classification,
        String description, String payee, BigDecimal amount, String memo) {}
    public record Card(String cardId, Instant modifiedAt, String cardName, String lastFourDigits,
        String useYn, BigDecimal amount, Instant monthlyModifiedAt) {}
    public record TaxPayment(String paymentId, int taxYear, String paymentType, LocalDate scheduledDate,
        BigDecimal paymentAmount, BigDecimal displayAmount, String paidYn, LocalDate actualPaymentDate,
        String status, Instant modifiedAt) {}
    public record TaxEstimate(int taxYear, BigDecimal estimatedTaxAmount, BigDecimal monthlyTaxAmount,
        boolean negativeTaxWarning, Instant modifiedAt) {}
    public record EstimateInput(int taxYear, BigDecimal estimatedTaxAmount, Instant modifiedAt) {}
    public record PaymentInput(String paymentId, LocalDate scheduledDate, BigDecimal paymentAmount,
        Instant modifiedAt) {}
    public record CardPayment(BigDecimal paymentAmount, boolean manualOverride, Instant modifiedAt) {}
    public record Summary(BigDecimal sgaAmount, BigDecimal nonOperatingIncomeAmount,
        BigDecimal nonOperatingExpenseAmount, BigDecimal monthlyTaxAmount, BigDecimal adjustmentAmount,
        BigDecimal cardTotalAmount, BigDecimal cardPaymentDifference) {}
    public record MonthStatus(String yearMonth, boolean closed, int itemCount, boolean future) {}
    public record Month(String yearMonth, boolean closed, String firstOpenYearMonth,
        List<MonthStatus> months, List<Item> items, List<Loan> loans, List<Adjustment> adjustments,
        List<Transfer> transfers, List<Card> cards, TaxEstimate taxEstimate,
        List<TaxPayment> taxPayments, CardPayment cardPayment, Summary summary,
        int activeCardCount, int autoFillCount, Instant modifiedAt) {}
    public record MonthInput(List<Item> items, List<Loan> loans, List<Adjustment> adjustments,
        List<Transfer> transfers, List<Card> cards, EstimateInput taxEstimate,
        List<PaymentInput> taxPayments, CardPayment cardPayment, Instant modifiedAt) {}
    public record ConfirmInput(LocalDate actualPaymentDate, BigDecimal actualPaymentAmount,
        Instant modifiedAt) {}
    public record TaxYear(TaxEstimate taxEstimate, List<TaxPayment> taxPayments) {}
    public record PreviousItems(List<Item> items, int count) {}
    public record Write<T>(String month, T row, String actor) {}
    public record Version(String month, String id, Instant modifiedAt) {}
    public record PaymentKey(String year, String type) {}
    public record PaymentWrite(PaymentKey key, ConfirmInput input, String actor) {}
    public record History(String table, String key, String field, String before, String after,
        String actor) {}
    public record Profit(String month, BigDecimal sga, BigDecimal income, BigDecimal expense,
        BigDecimal tax, BigDecimal adjustment) {}
    public record CashContribution(String yearMonth, String category, BigDecimal amount) {}
}
