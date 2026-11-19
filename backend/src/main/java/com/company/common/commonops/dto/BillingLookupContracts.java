package com.company.common.commonops.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class BillingLookupContracts {
    private BillingLookupContracts() {}
    public record ProjectOption(String projectId, String projectName, String customerName, String projectType) {}
    public record StatusOption(String code, String name) {}
    public record Options(List<ProjectOption> projects, List<StatusOption> statusOptions,
        String defaultBillMonth, LocalDate defaultBillDate, LocalDate defaultPayDueDate,
        BigDecimal estimatedSupplyAmount, BigDecimal vatRate) {}
    public record History(int step, LocalDate sentDate, String recipientDescription, String sendMethodCode, boolean resend) {}
    public record DunningRow(String billId, String projectId, String projectName, BigDecimal unpaidAmount,
        LocalDate payDueDate, int daysOverdue, Integer currentStep, List<History> history,
        String nextNotification, boolean canResend, String resendMessage) {}
    public record Card(int step, List<String> recipients) {}
    public record DunningResult(List<Card> cards, List<DunningRow> items) {}
}
