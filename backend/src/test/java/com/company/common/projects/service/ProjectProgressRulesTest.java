package com.company.common.projects.service;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.ProjectAmountRules;
import com.company.common.projects.dto.ProjectContracts.*;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.mapper.ProjectRows.*;
import com.company.common.support.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectProgressRulesTest {
    CalculationRuleService metadata;
    ProjectProgressRules rules;
    final LocalDate today = LocalDate.of(2026, 11, 15);
    Project project;
    HealthSource source;

    @BeforeEach void setup() throws Exception {
        metadata = mock(CalculationRuleService.class);
        // Read the packaged original definitions, not a second production formula source.
        String seed = new org.springframework.core.io.ClassPathResource("db/migration/V8__calculation_rule_seed.sql")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String id : List.of("식-16", "식-73", "식-75", "식-76")) {
            var row = java.util.regex.Pattern.compile("\\('" + id + "', '[^']*', '[^']*', '([^']*)'").matcher(seed);
            assertThat(row.find()).as(id).isTrue();
            when(metadata.definition(id)).thenReturn(new Definition(id, row.group(1), ""));
        }
        rules = new ProjectProgressRules(metadata);
        project = new Project(); project.projectId = "P-0001"; project.contractTypeCode = "IN";
        project.statusCode = "ING"; project.budgetAmount = new BigDecimal("1000");
        progress("50", "50");
        source = new HealthSource(); source.latestMemoDate = today;
    }
    void progress(String planned, String actual) {
        project.progress = new Progress(new BigDecimal(planned), new BigDecimal(actual), List.of());
    }
    Cost cost(String category, String amount) {
        var c = new Cost(); c.yearMonth = "202611"; c.costClassCode = category; c.totalAmount = new BigDecimal(amount); return c;
    }
    String light(List<Cost> costs) { return rules.trafficLight(project, source, costs, today); }

    @ParameterizedTest @CsvSource({"40,위험", "40.1,주의", "45,주의", "45.1,정상"})
    void scheduleThresholdsAreInclusiveAndUseDisplayedPrecision(String actual, String expected) {
        progress("50", actual);
        assertThat(light(List.of())).isEqualTo(expected);
    }
    @ParameterizedTest @CsvSource({"650,위험", "649,주의", "550,주의", "549,정상"})
    void turnkeyCostThresholdUsesBudgetAndActualProgress(String amount, String expected) {
        project.contractTypeCode = "TK";
        assertThat(light(List.of(cost("ALLOC", amount)))).isEqualTo(expected);
    }
    @ParameterizedTest @CsvSource({"MO", "DP"})
    void monthlyAndDispatchTypesNeverUseCostCriterion(String type) {
        project.contractTypeCode = type;
        assertThat(light(List.of(cost("ALLOC", "999999")))) .isEqualTo("정상");
    }
    @Test void additionalProjectExpensesContributeToTurnkeyCostPressure() {
        project.contractTypeCode = "TK";
        source.additionalExpenseAmount = new BigDecimal("150");
        assertThat(light(List.of(cost("ALLOC", "500")))).isEqualTo("위험");
    }
    @Test void overdueHighIssueWinsOverLesserWarnings() {
        source.overdueHighIssue = true; source.overdueIssue = true; source.latestMemoDate = today.minusDays(15);
        assertThat(light(List.of())).isEqualTo("위험");
    }
    @Test void overdueNonclosedIssueWarns() {
        source.overdueIssue = true;
        assertThat(light(List.of())).isEqualTo("주의");
    }
    @Test void memoFreshnessUsesWrittenDateWithStrictFourteenDayBoundary() {
        source.latestMemoDate = today.minusDays(14);
        assertThat(light(List.of())).isEqualTo("정상");
        source.latestMemoDate = today.minusDays(15);
        assertThat(light(List.of())).isEqualTo("주의");
    }
    @Test void missingProgressDoesNotBecomeZeroOrNormal() {
        project.progress = new Progress(null, BigDecimal.TEN, List.of());
        assertThat(light(List.of())).isNull();
    }
    @Test void missingMemoRemainsUnavailable() {
        source.latestMemoDate = null;
        assertThat(light(List.of())).isNull();
    }
    @Test void turnkeyWithoutCostSourcesRemainsUnavailable() {
        project.contractTypeCode = "TK";
        assertThat(light(List.of())).isNull();
    }
    @Test void zeroBudgetIsNotAnInventedCostRate() {
        project.contractTypeCode = "TK"; project.budgetAmount = BigDecimal.ZERO;
        assertThat(light(List.of(cost("ALLOC", "1")))).isNull();
    }
    @Test void lostPreCostsAreReadAsIdleWithoutChangingAmountsOrSourceObjects() {
        var before = cost("PRE", "123456");
        var result = rules.classifyCosts(List.of(before), "LOSE");
        assertThat(result.get(0).costClassCode).isEqualTo("IDLE");
        assertThat(result.get(0).totalAmount).isEqualByComparingTo(before.totalAmount);
        assertThat(before.costClassCode).isEqualTo("PRE");
        assertThat(rules.classifyCosts(List.of(before), "PRE").get(0).costClassCode).isEqualTo("PRE");
    }
    @Test void unknownTrafficDefinitionIsRejectedInsteadOfEvaluated() {
        when(metadata.definition("식-76")).thenReturn(new Definition("식-76", "Runtime.exec()", ""));
        assertThatThrownBy(() -> light(List.of())).isInstanceOf(IllegalStateException.class).hasMessageContaining("식-76");
    }
    @Test void listAndDetailUseTheSamePersistedHealthInputsAndLossCostAdapter() {
        var mapper = mock(ProjectMapper.class); var calendar = mock(BusinessCalendar.class);
        when(calendar.today()).thenReturn(today); when(calendar.currentMonth()).thenReturn(YearMonth.from(today));
        project.startDate = today.minusDays(10); project.endDate = today.plusDays(10);
        var step = new Step(); step.weightRate = new BigDecimal("100");
        step.plannedStartDate = today.minusDays(1); step.plannedEndDate = today.plusDays(2);
        when(mapper.steps(project.projectId)).thenReturn(List.of(step));
        when(mapper.actualProgress(project.projectId)).thenReturn(new BigDecimal("50"));
        when(mapper.healthSource(project.projectId, today)).thenReturn(source);
        when(mapper.costs(project.projectId, "202611")).thenReturn(List.of(cost("PRE", "123456")));
        when(mapper.selectProject(project.projectId, today, "202611")).thenReturn(project);
        when(mapper.selectProjects(today, "202611")).thenReturn(List.of(project));
        var service = new CommonProjectService(mapper, calendar, new CurrentBusinessActor(),
            mock(ProjectAmountRules.class), new ExcelExportService(), rules);
        source.overdueHighIssue = true;
        assertThat(service.getCommonProjects().items().get(0).trafficLight).isEqualTo("위험");
        assertThat(service.getCommonProject(project.projectId).trafficLight).isEqualTo("위험");
        project.statusCode = "LOSE";
        assertThat(service.getProjectCosts(project.projectId).get(0).costClassCode).isEqualTo("IDLE");
        verify(mapper, never()).updateProject(any());
        verify(mapper, times(2)).healthSource(project.projectId, today);
    }
}
