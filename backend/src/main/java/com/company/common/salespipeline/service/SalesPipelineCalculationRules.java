package com.company.common.salespipeline.service;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.SalesPipelineApprovalStandards;
import com.company.common.common.SalesPipelineCodes;
import com.company.common.common.SalesPipelineDefinitions;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.Need;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.salesopportunities.service.SalesOpportunityRules;
import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SalesPipelineCalculationRules {
    private final SalesPipelineDefinitions definitions;
    private final SalesPipelineApprovalStandards approvals;
    private final CalculationRuleService standards;
    private final SalesOpportunityMapper costs;
    private final SalesPipelineCodes codes;
    private final BusinessCalendar calendar;

    public SalesPipelineCalculationRules(SalesPipelineDefinitions definitions,
        SalesPipelineApprovalStandards approvals, CalculationRuleService standards,
        SalesOpportunityMapper costs, SalesPipelineCodes codes, BusinessCalendar calendar) {
        this.definitions = definitions;
        this.approvals = approvals;
        this.standards = standards;
        this.costs = costs;
        this.codes = codes;
        this.calendar = calendar;
    }

    public List<Definition> definitions() { return definitions.definitions(); }

    public static Definition require(List<Definition> loaded, String id, String... fragments) {
        var definition = loaded.stream().filter(d -> id.equals(d.calcId())).findFirst()
            .orElseThrow(() -> CodeFailure.invalid("calcId", "등록된 계산 정의를 확인해 주세요."));
        String text = definition.formula() + " " + definition.details();
        for (String fragment : fragments) {
            if (!text.contains(fragment))
                throw CodeFailure.invalid("calcId", id + " 지원하는 계산 정의를 확인해 주세요.");
        }
        return definition;
    }

    public BigDecimal standard(String code) {
        var value = standards.effectiveStandard(code, calendar.currentMonth()).value();
        if (value == null) throw CodeFailure.invalid("standardCode", "운영 기준값을 확인해 주세요.");
        return value;
    }

    public Quote quote(List<Need> needs) {
        var loaded = definitions();
        require(loaded, "식-00", "원 단위");
        require(loaded, "식-83", "같은 직무·등급", "만 원", "급여");
        require(loaded, "식-84", "인원 × 월 MM × 개월", "예상 매출 − 예상 원가", "× 100");
        require(loaded, "식-85", "최소 단가 = 1인 월 원가", "만 원", "백만 원", "올림");
        require(loaded, "식-86", "승인 필요", "기준 충족");
        var approval = approvals.effective(calendar.currentMonth());
        BigDecimal target = standard("TGT_MRGN_RT");
        if (target.signum() < 0 || target.compareTo(BigDecimal.valueOf(100)) >= 0)
            throw CodeFailure.invalid("standardCode", "목표 이익률을 확인해 주세요.");
        BigDecimal divisor = BigDecimal.ONE.subtract(target.movePointLeft(2));
        BigDecimal revenue = BigDecimal.ZERO, cost = BigDecimal.ZERO, totalMm = BigDecimal.ZERO;
        var rows = new ArrayList<NeedQuote>();
        for (int i = 0; i < needs.size(); i++) {
            Need need = needs.get(i);
            String prefix = "needs[" + i + "].";
            if (codes.selectable("JOB").stream().noneMatch(c -> need.jobCode().equals(c.code)))
                throw SalesOpportunityRules.invalid(prefix + "jobCode");
            if (need.gradeCode() != null && codes.selectable("GRADE").stream().noneMatch(c -> need.gradeCode().equals(c.code)))
                throw SalesOpportunityRules.invalid(prefix + "gradeCode");
            BigDecimal unitCost = costs.monthlyCost(need.jobCode(), need.gradeCode());
            if (unitCost == null || unitCost.signum() <= 0)
                throw CodeFailure.invalid(prefix + "jobCode", (i + 1) + "번째 줄의 월 원가 자료를 확인해 주세요.");
            BigDecimal price = need.estimatedUnitPrice() == null ? costs.defaultPrice(need.jobCode()) : need.estimatedUnitPrice();
            if (price == null || price.signum() <= 0)
                throw CodeFailure.invalid(prefix + "estimatedUnitPrice", "직무 기준 단가를 확인해 주세요.");
            var mm = BigDecimal.valueOf(need.requiredCount()).multiply(need.monthlyMm())
                .multiply(BigDecimal.valueOf(need.months()));
            totalMm = totalMm.add(mm);
            revenue = revenue.add(mm.multiply(price));
            cost = cost.add(mm.multiply(unitCost));
            rows.add(new NeedQuote(i, unitCost, ceiling(unitCost.divide(divisor, MathContext.DECIMAL128), 4)));
        }
        BigDecimal rate = revenue.subtract(cost).divide(revenue, MathContext.DECIMAL128).movePointRight(2);
        String band = rate.signum() < 0 ? "손해 예상" : rate.compareTo(approval.approvalRate()) < 0
            ? "승인 필요" : rate.compareTo(target) < 0 ? "목표 미달" : "기준 충족";
        BigDecimal minimum = cost.divide(totalMm, MathContext.DECIMAL128);
        return new Quote(revenue.setScale(0, RoundingMode.DOWN), cost.setScale(0, RoundingMode.DOWN),
            rate, minimum, ceiling(minimum.divide(divisor, MathContext.DECIMAL128), 4),
            cost.setScale(0, RoundingMode.DOWN), ceiling(cost.divide(divisor, MathContext.DECIMAL128), 6),
            approval.approvalRate(), approval.reapprovalGap(), band, List.copyOf(rows));
    }

    private static BigDecimal ceiling(BigDecimal value, int power) {
        return value.movePointLeft(power).setScale(0, RoundingMode.CEILING).movePointRight(power);
    }
}
