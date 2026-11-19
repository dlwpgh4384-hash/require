package com.company.common.personalcostprofits;

import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.personalcostprofits.service.PersonalCostProfitColumnPolicy;
import com.company.common.personalcostprofits.service.PersonalCostProfitRules;
import com.company.common.personalcostprofits.service.PersonalCostProfitRules.Rates;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.company.common.personalcostprofits.PersonalCostProfitTestSources.*;
import static org.assertj.core.api.Assertions.*;

class PersonalCostProfitRulesTest {
    final Rates rates = new Rates(n("200"), n("400"), n("8.33"), n("3.3"));
    @Test void moneyTruncatesTowardZeroForNegativeAllocations() {
        assertThat(PersonalCostProfitRules.money(n("-1.9"))).isEqualByComparingTo("-1");
    }
    @Test void deterministicFinalAssignmentReceivesResidualAndDuplicateProjectsRemainSeparate() {
        var costs = PersonalCostProfitRules.live(person("A"), pay("A", "202602", "1001"),
            List.of(assignment("B", "202602", "0.5"), assignment("A", "202602", "0.5")), "202602", rates, false);
        assertThat(costs).hasSize(2);
        assertThat(costs.get(0).assignmentId()).isEqualTo("A");
        assertThat(costs.get(0).labor()).isEqualByComparingTo("550");
        assertThat(costs.get(1).labor()).isEqualByComparingTo("551");
        assertThat(costs.stream().map(Cost::labor).reduce(n("0"), java.math.BigDecimal::add)).isEqualByComparingTo("1101");
    }
    @Test void payrollInsuranceIsUploadedAndHistoricalEmployeeTypeControlsRetirement() {
        var person = person("A");
        var uploaded = new Pay("202602", "A", "FT", n("1000"), n("0"), n("175"), n("0"), n("10"), n("0"), n("12"), n("0"), n("0"));
        var money = PersonalCostProfitRules.payroll(person, uploaded, rates, false);
        assertThat(money.get("companyInsurance")).isEqualByComparingTo("175");
        assertThat(money.get("retirementReserve")).isEqualByComparingTo("83");
        assertThat(money.get("personalCost")).isEqualByComparingTo("1258");
        assertThat(money.get("netPay")).isEqualByComparingTo("978");
    }
    @Test void closedPayrollKeepsUploadedWithholdingWhileLiveTaxIsDeductedExactlyOnce() {
        var uploaded = pay("A", "202602", "1000");
        assertThat(PersonalCostProfitRules.payroll(person("A"), uploaded, rates, true).get("withholdingTax")).isEqualByComparingTo("33");
        assertThat(PersonalCostProfitRules.payroll(person("A"), uploaded, rates, false).get("netPay")).isEqualByComparingTo("945");
    }
    @Test void noUploadOnLeaveMeansNoSyntheticCostAndUploadedLeaveUsesAnonymousIdle() {
        assertThat(PersonalCostProfitRules.live(person("A"), null, List.of(), "202602", rates, true)).isEmpty();
        var result = PersonalCostProfitRules.live(person("A"), pay("A", "202602", "1000"), List.of(), "202602", rates, true);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).assignmentId()).isEqualTo("IDLE");
        assertThat(result.get(0).labor()).isEqualByComparingTo("1100");
        assertThat(result.get(0).team()).isZero();
    }
    @Test void turnkeyDistributionOverridesPoolRateAndFallbackUsesCostContractBudget() {
        var distributed = new Assignment("202602", "A", "P", "A", "프로젝트", "백엔드", "TK", "PAID",
            n("1"), n("999999"), n("0"), n("0"), n("10000"), n("8000"), n("500"));
        assertThat(PersonalCostProfitRules.live(person("A"), pay("A", "202602", "1000"),
            List.of(distributed), "202602", rates, false).get(0).revenue()).isEqualByComparingTo("500");
        var fallback = new Assignment("202602", "A", "P", "A", "프로젝트", "백엔드", "TK", "PAID",
            n("1"), n("999999"), n("0"), n("0"), n("10000"), n("8000"), null);
        assertThat(PersonalCostProfitRules.live(person("A"), pay("A", "202602", "1000"),
            List.of(fallback), "202602", rates, false).get(0).revenue()).isEqualByComparingTo("1375");
    }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "EXEC", "FIN"})
    void enabledSensitiveRoleAllowsSalaryAndCost(String id) {
        assertThat(PersonalCostProfitColumnPolicy.columns(null, List.of(role(id, "Y"))))
            .contains("monthlySalary", "personalCost");
    }
    @Test void multipleRolesUseUnionAndAnAdminFlagIsNotBypassed() {
        assertThat(PersonalCostProfitColumnPolicy.columns(null, List.of(role("ADMIN", "N"))))
            .doesNotContain("monthlySalary", "personalCost");
        assertThat(PersonalCostProfitColumnPolicy.columns(null, List.of(role("PM", "N"), role("FIN", "Y"))))
            .contains("monthlySalary", "personalCost");
        var source = new LinkedHashMap<String, Object>();
        source.put("monthlySalary", n("123")); source.put("personalCost", n("456")); source.put("totalCost", n("500"));
        assertThat(PersonalCostProfitColumnPolicy.project(source, PersonalCostProfitColumnPolicy.columns(null, List.of(role("PM", "N")))))
            .doesNotContainKeys("monthlySalary", "personalCost").containsKey("totalCost");
    }
    @Test void workbookColumnsIntersectPolicyAndCanonicalOrder() {
        assertThat(PersonalCostProfitColumnPolicy.columns(List.of("incomeTax", "personalCost", "developerName", "unitPrice"), List.of(role("PM", "N"))))
            .containsExactly("developerName", "unitPrice", "incomeTax");
    }
    @Test void disabledSensitiveRoleDoesNotGrantSensitiveColumns() {
        var disabled = new com.company.common.roles.dto.RoleContracts.Role("FIN", "재무", "ALL", "Y", "N", true);
        assertThat(PersonalCostProfitColumnPolicy.columns(null, List.of(disabled)))
            .doesNotContain("monthlySalary", "personalCost");
    }
    @Test void retiredMonthIsIncludedAndFollowingMonthIsNot() {
        var p = person("A");
        var retired = new Person(p.developerId(), p.developerName(), p.affiliationId(), p.affiliationName(),
            p.positionCode(), p.positionName(), p.employeeTypeCode(), p.employeeTypeName(), p.joinDate(),
            java.time.LocalDate.of(2026,2,1), p.jobCode(), p.jobName(), p.gradeCode(), p.gradeName(), p.salesPartId(), p.directClassCode());
        assertThat(PersonalCostProfitRules.employed(retired, java.time.YearMonth.of(2026,2))).isTrue();
        assertThat(PersonalCostProfitRules.employed(retired, java.time.YearMonth.of(2026,3))).isFalse();
    }
}
