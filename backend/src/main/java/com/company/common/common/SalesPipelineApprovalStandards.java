package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SalesPipelineApprovalStandards {
    private final CalculationRuleService standards;

    public SalesPipelineApprovalStandards(CalculationRuleService standards) {
        this.standards = standards;
    }

    public record Values(BigDecimal approvalRate, BigDecimal reapprovalGap) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Values effective(YearMonth month) {
        if (month == null) {
            throw CodeFailure.invalid("effectiveMonth", "적용 월을 확인해 주세요.");
        }
        return new Values(value("QUOTE_APRV_RT", month), value("QUOTE_REAPRV_GAP", month));
    }

    /** effective와 같은 판단이지만, 쓸 수 있는 기준값이 없으면 오류 대신 null(트랜잭션을 롤백 전용으로 만들지 않음). */
    @Transactional(readOnly = true)
    public Values effectiveOrNull(YearMonth month) {
        if (month == null) return null;
        BigDecimal rate = valueOrNull("QUOTE_APRV_RT", month), gap = valueOrNull("QUOTE_REAPRV_GAP", month);
        return rate == null || gap == null ? null : new Values(rate, gap);
    }

    private BigDecimal valueOrNull(String code, YearMonth month) {
        Standard standard = standards.findEffectiveStandard(code, month);
        if (standard == null || !code.equals(standard.standardCode()) || standard.value() == null
            || standard.effectiveMonth() == null || standard.effectiveMonth().isAfter(month)) return null;
        return standard.value();
    }

    private BigDecimal value(String code, YearMonth month) {
        Standard standard = standards.effectiveStandard(code, month);
        if (standard == null || !code.equals(standard.standardCode()) || standard.value() == null
            || standard.effectiveMonth() == null || standard.effectiveMonth().isAfter(month)) {
            throw CodeFailure.invalid("standardCode", "적용 가능한 견적 승인 기준값을 확인해 주세요.");
        }
        return standard.value();
    }
}
