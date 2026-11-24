package com.company.common.salesopportunities.service;

import com.company.common.common.SalesPipelineApprovalStandards;
import com.company.common.common.SalesPipelineDefinitions;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.Need;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SalesOpportunityCalculations {
    private final SalesPipelineDefinitions definitions;
    private final SalesPipelineApprovalStandards approvals;
    private final CalculationRuleService standards;
    private final SalesOpportunityMapper mapper;
    private final BusinessCalendar calendar;

    public SalesOpportunityCalculations(SalesPipelineDefinitions definitions, SalesPipelineApprovalStandards approvals,
        CalculationRuleService standards, SalesOpportunityMapper mapper, BusinessCalendar calendar) {
        this.definitions = definitions;
        this.approvals = approvals;
        this.standards = standards;
        this.mapper = mapper;
        this.calendar = calendar;
    }
    public record Evaluation(BigDecimal profitRate, SalesPipelineApprovalStandards.Values standards) {}

    public BigDecimal stageProbability(String stage) {
        String suffix = switch (stage) {
            case "FIND" -> "1"; case "PROP" -> "2"; case "NEGO" -> "3"; case "PRE" -> "4";
            default -> throw SalesOpportunityRules.invalid("stageCode");
        };
        return standards.effectiveStandard("STEP_PRBL_" + suffix, calendar.currentMonth()).value();
    }
    public BigDecimal price(Need need) {
        var price = need.estimatedUnitPrice() == null ? mapper.defaultPrice(need.jobCode()) : need.estimatedUnitPrice();
        if (price == null || price.signum() <= 0 || price.precision() - price.scale() > 15)
            throw CodeFailure.invalid("estimatedUnitPrice", "직무 기준 단가를 확인해 주세요.");
        return price;
    }
    public Evaluation evaluate(List<Need> needs) {
        var loaded = definitions.definitions();
        for (var id : List.of("식-83", "식-84", "식-86")) {
            var definition = loaded.stream().filter(d -> id.equals(d.calcId())).findFirst()
                .orElseThrow(() -> CodeFailure.invalid("calcId", "견적 계산 정의를 확인해 주세요."));
            boolean supported = switch (id) {
                case "식-83" -> definition.formula().contains("같은 직무·등급") && definition.details().contains("만 원");
                case "식-84" -> definition.formula().contains("인원 × 월 MM × 개월")
                    && definition.formula().contains("예상 매출 − 예상 원가") && definition.formula().contains("× 100");
                default -> definition.formula().contains("AUTO_REL") && definition.formula().contains("반려");
            };
            if (!supported) throw CodeFailure.invalid("calcId", "지원하는 견적 계산 정의를 확인해 주세요.");
        }
        BigDecimal revenue = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        for (int i = 0; i < needs.size(); i++) {
            var need = needs.get(i);
            var unitCost = mapper.monthlyCost(need.jobCode(), need.gradeCode());
            if (unitCost == null || unitCost.signum() <= 0)
                throw CodeFailure.invalid("needs[" + i + "].jobCode", (i + 1) + "번째 줄의 월 원가 자료를 확인해 주세요.");
            var mm = BigDecimal.valueOf(need.requiredCount()).multiply(need.monthlyMm()).multiply(BigDecimal.valueOf(need.months()));
            revenue = revenue.add(mm.multiply(price(need)));
            cost = cost.add(mm.multiply(unitCost));
        }
        var rate = revenue.subtract(cost).multiply(BigDecimal.valueOf(100)).divide(revenue, MathContext.DECIMAL128);
        return new Evaluation(rate, approvals.effective(calendar.currentMonth()));
    }
    public static String approval(String oldStatus, BigDecimal baseline, Evaluation evaluation) {
        if ("REJ".equals(oldStatus)) return "WAIT";
        if ("OK".equals(oldStatus) && baseline != null
            && evaluation.profitRate().subtract(baseline).abs().compareTo(evaluation.standards().reapprovalGap()) <= 0) return "OK";
        return evaluation.profitRate().compareTo(evaluation.standards().approvalRate()) < 0 ? "WAIT" : "NONE";
    }
    public static BigDecimal storedProfit(Evaluation evaluation) {
        return evaluation.profitRate().setScale(2, RoundingMode.HALF_UP);
    }
}
