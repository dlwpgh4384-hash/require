package com.company.common.salesopportunities;

import com.company.common.common.SalesPipelineApprovalStandards.Values;
import com.company.common.salesopportunities.service.SalesOpportunityCalculations;
import com.company.common.salesopportunities.service.SalesOpportunityRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SalesOpportunityRulesTest {
    SalesOpportunityCalculations.Evaluation evaluation(String rate) {
        return new SalesOpportunityCalculations.Evaluation(new BigDecimal(rate), new Values(new BigDecimal("15"), new BigDecimal("0.5")));
    }
    @Test void initialComparisonIsStrictAndUsesFullPrecision() {
        assertThat(SalesOpportunityCalculations.approval(null, null, evaluation("15.000"))).isEqualTo("NONE");
        assertThat(SalesOpportunityCalculations.approval(null, null, evaluation("14.99999"))).isEqualTo("WAIT");
    }
    @Test void exactReapprovalGapMaintainsApprovalInBothDirections() {
        assertThat(SalesOpportunityCalculations.approval("OK", new BigDecimal("14"), evaluation("14.5"))).isEqualTo("OK");
        assertThat(SalesOpportunityCalculations.approval("OK", new BigDecimal("14"), evaluation("13.5"))).isEqualTo("OK");
    }
    @Test void exceedingGapBelowThresholdRequestsApproval() {
        assertThat(SalesOpportunityCalculations.approval("OK", new BigDecimal("14"), evaluation("13.499"))).isEqualTo("WAIT");
    }
    @Test void exceedingGapAtThresholdReleasesApproval() {
        assertThat(SalesOpportunityCalculations.approval("OK", new BigDecimal("14"), evaluation("15"))).isEqualTo("NONE");
    }
    @Test void rejectedSaveAlwaysRequestsApprovalEvenAboveThreshold() {
        assertThat(SalesOpportunityCalculations.approval("REJ", null, evaluation("35"))).isEqualTo("WAIT");
    }
    @Test void changedEffectiveStandardsChangeDecisionWithoutConstants() {
        var evaluation = new SalesOpportunityCalculations.Evaluation(new BigDecimal("17"), new Values(new BigDecimal("18"), new BigDecimal("2")));
        assertThat(SalesOpportunityCalculations.approval(null, null, evaluation)).isEqualTo("WAIT");
        assertThat(SalesOpportunityCalculations.approval("OK", new BigDecimal("15"), evaluation)).isEqualTo("OK");
    }
    @Test void unicodeReasonLengthCountsCharactersNotUtf16Units() {
        var body = new ObjectMapper().createObjectNode().put("reason", "😀".repeat(300));
        assertThat(SalesOpportunityRules.text(body, "reason", 300, false)).isEqualTo("😀".repeat(300));
        body.put("reason", "😀".repeat(301));
        assertThatThrownBy(() -> SalesOpportunityRules.text(body, "reason", 300, false)).isInstanceOf(com.company.common.support.service.CodeFailure.class);
    }
}
