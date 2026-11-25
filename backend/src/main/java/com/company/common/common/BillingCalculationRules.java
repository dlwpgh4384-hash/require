package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/** Typed billing operations; definitions and effective thresholds are read through the existing DB service. */
@Component
public class BillingCalculationRules {
    private final CalculationRuleService metadata;

    public BillingCalculationRules(CalculationRuleService metadata) {
        this.metadata = metadata;
    }

    public record MonthlyInput(String contractTypeCode, String inputTypeCode,
                               BigDecimal monthlyMm, BigDecimal billingPrice) {}
    public record Amounts(BigDecimal supplyAmount, BigDecimal vatRate,
                          BigDecimal vatAmount, BigDecimal totalAmount) {}
    public record CheckResult(String judgement, BigDecimal difference, BigDecimal threshold) {}

    public List<Definition> definitions() {
        return List.of("식-00", "식-01", "식-02", "식-04", "식-05", "식-06", "식-07",
            "식-09", "식-10", "식-47", "식-57", "식-58", "식-88", "식-89")
            .stream().map(metadata::definition).toList();
    }

    /** monthlyMm and billingPrice are the selected month's TB_PJ_ASGN_MON values, not current assignment values. */
    public BigDecimal estimate(List<MonthlyInput> inputs) {
        definition("식-02");
        BigDecimal amount = BigDecimal.ZERO;
        for (MonthlyInput input : inputs) {
            if (!List.of("MO", "DP").contains(input.contractTypeCode())
                    || "FREE".equals(input.inputTypeCode())) continue;
            nonnegative(input.monthlyMm(), "monthlyMm");
            nonnegative(input.billingPrice(), "billingPrice");
            amount = amount.add(input.monthlyMm().multiply(input.billingPrice()).setScale(0, RoundingMode.DOWN));
        }
        return amount;
    }

    /** 부가세율 기본값: 운영 기준값 VAT_RT(그 달 적용분). 기준값이 없을 때만 10. */
    public BigDecimal defaultVatRate(YearMonth month) {
        try {
            BigDecimal value = metadata.effectiveStandard("VAT_RT", month).value();
            if (value == null) return BigDecimal.TEN;
            rate(value, "vatRate");
            return value;
        } catch (CodeFailure missing) {
            return BigDecimal.TEN;
        }
    }

    public Amounts amounts(BigDecimal supplyAmount, BigDecimal vatRate) {
        definition("식-05");
        positiveMoney(supplyAmount, "supplyAmount");
        rate(vatRate, "vatRate");
        BigDecimal vat = supplyAmount.multiply(vatRate).movePointLeft(2).setScale(0, RoundingMode.DOWN);
        BigDecimal total = supplyAmount.add(vat);
        money(total, "totalAmount");
        return new Amounts(supplyAmount, vatRate, vat, total);
    }

    public BigDecimal balance(BigDecimal totalAmount, BigDecimal normalReceiptSum) {
        definition("식-06");
        money(totalAmount, "totalAmount");
        money(normalReceiptSum, "receiptSumAmount");
        BigDecimal result = totalAmount.subtract(normalReceiptSum);
        if (result.signum() < 0) throw CodeFailure.invalid("amount", "입금액은 미수금 이하로 입력해 주세요.");
        return result;
    }

    /** Existing BILL_STAT codes; names/options remain owned by TB_SY_CODE. Never persist this result. */
    public String status(boolean cancelled, BigDecimal unpaidAmount, LocalDate payDueDate, LocalDate today) {
        definition("식-06");
        if (cancelled) return "CNCL";
        nonnegative(unpaidAmount, "unpaidAmount");
        if (unpaidAmount.signum() == 0) return "PAID";
        return payDueDate.isBefore(today) ? "LATE" : "UNPAID";
    }

    public CheckResult check(BigDecimal referenceAmount, BigDecimal uncancelledSupplyAmount, YearMonth month) {
        definition("식-88");
        nonnegative(referenceAmount, "referenceAmount");
        nonnegative(uncancelledSupplyAmount, "billedSupplyAmount");
        BigDecimal minimum = standard("BILL_MISS_MIN", month);
        BigDecimal percentage = standard("BILL_MISS_RT", month);
        rate(percentage, "BILL_MISS_RT");
        BigDecimal threshold = minimum.max(referenceAmount.multiply(percentage).movePointLeft(2));
        BigDecimal difference = referenceAmount.subtract(uncancelledSupplyAmount);
        String judgement;
        if (uncancelledSupplyAmount.signum() == 0) judgement = "청구 없음";
        else if (difference.abs().compareTo(threshold) <= 0) judgement = "맞음";
        else judgement = difference.signum() > 0 ? "적게 청구" : "많이 청구";
        return new CheckResult(judgement, difference, threshold);
    }

    /** The caller supplies Korean today (BusinessCalendar); overdue status starts before notification stage 1. */
    public Integer dunningStep(boolean cancelled, BigDecimal unpaidAmount, LocalDate payDueDate, LocalDate today) {
        definition("식-89");
        if (cancelled || unpaidAmount.signum() <= 0) return null;
        YearMonth month = YearMonth.from(today);
        int first = days("DUN_D1", month);
        int second = days("DUN_D2", month);
        int third = days("DUN_D3", month);
        if (first < 1 || first >= second || second >= third) {
            throw CodeFailure.invalid("standardCode", "미수 알림 단계 기준을 확인해 주세요.");
        }
        long elapsed = ChronoUnit.DAYS.between(payDueDate, today);
        if (elapsed >= third) return 3;
        if (elapsed >= second) return 2;
        return elapsed >= first ? 1 : null;
    }

    public LocalDate nextDunningDate(boolean cancelled, BigDecimal unpaidAmount,
        LocalDate payDueDate, LocalDate today) {
        Integer step = dunningStep(cancelled, unpaidAmount, payDueDate, today);
        if (cancelled || unpaidAmount.signum() <= 0 || Integer.valueOf(3).equals(step)) return null;
        int next = step == null ? 1 : step + 1;
        return payDueDate.plusDays(days("DUN_D" + next, YearMonth.from(today)));
    }

    private void definition(String calcId) {
        metadata.definition("식-00");
        metadata.definition(calcId);
    }

    private BigDecimal standard(String code, YearMonth month) {
        BigDecimal value = metadata.effectiveStandard(code, month).value();
        nonnegative(value, code);
        return value;
    }

    private int days(String code, YearMonth month) {
        try {
            return standard(code, month).intValueExact();
        } catch (ArithmeticException exception) {
            throw CodeFailure.invalid("standardCode", "미수 알림 일수는 정수여야 합니다.");
        }
    }

    public static void positiveMoney(BigDecimal value, String field) {
        money(value, field);
        if (value.signum() == 0) throw CodeFailure.invalid(field, "0보다 큰 금액을 입력해 주세요.");
    }

    public static void money(BigDecimal value, String field) {
        nonnegative(value, field);
        if (value.stripTrailingZeros().scale() > 0
                || value.compareTo(BigDecimal.TEN.pow(15)) >= 0) {
            throw CodeFailure.invalid(field, "금액은 15자리 이내의 원 단위 정수로 입력해 주세요.");
        }
    }

    public static void rate(BigDecimal value, String field) {
        nonnegative(value, field);
        if (value.compareTo(BigDecimal.valueOf(100)) > 0 || value.stripTrailingZeros().scale() > 2) {
            throw CodeFailure.invalid(field, "비율은 0~100, 소수 둘째 자리까지 입력해 주세요.");
        }
    }

    private static void nonnegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) throw CodeFailure.invalid(field, "0 이상의 값을 입력해 주세요.");
    }
}
