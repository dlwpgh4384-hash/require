package com.company.common.raterenegotiations.service;

import com.company.common.raterenegotiations.dto.RateRenegotiationContracts.*;
import com.company.common.raterenegotiations.mapper.RateRenegotiationRows.AssignmentInfo;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import static java.math.BigDecimal.*;

/** Semantic arithmetic; formula descriptions remain live DB metadata, never executable text. */
public final class RateRenegotiationRules {
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = valueOf(100);
    private static final BigDecimal TEN_THOUSAND = valueOf(10000);
    private RateRenegotiationRules() {}

    public static Filter normalize(Filter f) {
        int months = f.renewalMonths() == null ? 6 : f.renewalMonths();
        if (!Set.of(3, 6, 12).contains(months)) {
            throw CodeFailure.invalid("renewalMonths", "갱신 시점은 3개월, 6개월, 12개월 중 선택해 주세요.");
        }
        BigDecimal threshold = f.profitRateThreshold() == null ? valueOf(15) : f.profitRateThreshold();
        if (Set.of(10, 15, 20).stream().noneMatch(v -> threshold.compareTo(valueOf(v)) == 0)) {
            throw CodeFailure.invalid("profitRateThreshold", "이익률은 10%, 15%, 20% 중 선택해 주세요.");
        }
        BigDecimal increase = f.increaseRate() == null ? valueOf(5) : f.increaseRate().max(ZERO).min(valueOf(50));
        return new Filter(months, threshold, f.employeeTypeCode(), f.projectId(), increase);
    }
    public static boolean eligible(AssignmentInfo a, BigDecimal mm, Filter f, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, a.renewalDate());
        int horizon = switch (f.renewalMonths()) { case 3 -> 92; case 6 -> 183; default -> 366; };
        return !"CNCL".equals(a.assignmentStatus()) && !"FREE".equals(a.inputTypeCode())
            && "ING".equals(a.projectStatus()) && Set.of("MO", "DP").contains(a.contractTypeCode())
            && days >= 0 && days <= horizon && mm.signum() > 0 && a.currentUnitPrice().signum() > 0
            && (f.employeeTypeCode() == null || f.employeeTypeCode().isEmpty() || f.employeeTypeCode().equals(a.employeeTypeCode()))
            && (f.projectId() == null || f.projectId().isEmpty() || f.projectId().equals(a.projectId()));
    }
    public static BigDecimal exactProfit(BigDecimal revenue, BigDecimal labor) {
        return revenue.subtract(labor).multiply(HUNDRED).divide(revenue, PRECISION);
    }
    public static boolean belowThreshold(BigDecimal revenue, BigDecimal labor, BigDecimal threshold) {
        // Cross multiplication avoids rounding a repeating ratio at the eligibility boundary.
        return revenue.subtract(labor).multiply(HUNDRED).compareTo(threshold.multiply(revenue)) < 0;
    }
    public static Row row(AssignmentInfo a, BigDecimal mm, BigDecimal labor,
        BigDecimal targetRate, BigDecimal increase, LocalDate today) {
        if (targetRate.signum() < 0 || targetRate.compareTo(HUNDRED) >= 0) {
            throw CodeFailure.invalid("source", "목표 이익률 운영 기준값을 확인해 주세요.");
        }
        BigDecimal denominator = mm.multiply(ONE.subtract(targetRate.movePointLeft(2))).multiply(TEN_THOUSAND);
        BigDecimal target = labor.divide(denominator, 0, RoundingMode.CEILING).multiply(TEN_THOUSAND);
        BigDecimal needed = target.subtract(a.currentUnitPrice()).multiply(HUNDRED)
            .divide(a.currentUnitPrice(), 1, RoundingMode.HALF_UP);
        return new Row(a.assignmentId(), a.developerId(), a.developerName(), a.employeeTypeName(), a.projectName(),
            a.renewalDate(), Math.toIntExact(ChronoUnit.DAYS.between(today, a.renewalDate())), mm,
            a.currentUnitPrice(), labor, exactProfit(a.currentUnitPrice().multiply(mm), labor).setScale(1, RoundingMode.HALF_UP),
            target, needed, annual(a.currentUnitPrice().multiply(increase.movePointLeft(2)), a.contractMm()),
            a.projectId(), a.managementNumber());
    }
    private static BigDecimal annual(BigDecimal difference, BigDecimal contractMm) {
        return difference.multiply(contractMm).multiply(valueOf(12)).setScale(0, RoundingMode.DOWN);
    }
    public static BigDecimal targetAnnual(Row row, BigDecimal contractMm) {
        return annual(row.targetUnitPrice().subtract(row.currentUnitPrice()), contractMm);
    }
}
