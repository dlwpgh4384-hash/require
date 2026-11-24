package com.company.common.commonops;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.BusinessPlanComparisonDefinitions;
import com.company.common.commonops.controller.CommonBusinessPlanComparisonController;
import com.company.common.commonops.mapper.BusinessPlanComparisonMapper;
import com.company.common.commonops.mapper.BusinessPlanComparisonRows.Target;
import com.company.common.commonops.service.CommonBusinessPlanComparisonService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.mapper.ProfitStructureRows.*;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.support.controller.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonBusinessPlanComparisonController.class)
@ContextConfiguration(classes = {CommonBusinessPlanComparisonController.class,
    CommonBusinessPlanComparisonService.class, BusinessPlanComparisonDefinitions.class,
    ProfitStructureMonthlyProfitService.class, UserPreferencePeriodBasis.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class})
class BusinessPlanComparisonControllerTest {
    static final String PATH = "/api/common/business-plan-comparisons";
    @Autowired MockMvc mvc;
    @Autowired ProfitStructureMonthlyProfitService monthly;
    @MockBean ProfitStructureMapper source;
    @MockBean BusinessPlanComparisonMapper targets;
    @MockBean CalculationRuleService calculation;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean PlatformTransactionManager transactions;

    static BigDecimal n(long value) { return BigDecimal.valueOf(value); }
    static Snapshot saved(String month) {
        return new Snapshot(month, n(300), null, null, null, n(200), n(100), n(150),
            null, n(-50), null, null, null, null, "N", "Y", null);
    }
    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(periods.selectCashBaseYm()).thenReturn(n(202601));
        when(periods.selectClosedMonths()).thenReturn(List.of("202610"));
        var rows = new ArrayList<Snapshot>();
        var plans = new ArrayList<Target>();
        for (int m = 1; m <= 12; m++) {
            String ym = "2026" + String.format("%02d", m);
            rows.add(saved(ym));
            plans.add(new Target(ym, n(200), n(100)));
        }
        when(source.snapshots(any())).thenReturn(rows);
        when(targets.targets(2026)).thenReturn(plans);
    }
    @Test void publicHappyPathHasTwelveMonthsAndSameRevenueAndProfitAsUi08Source() throws Exception {
        var sourceMonth = monthly.year(2026, 11, "202601").get("202611").values();
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "202611"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.months.length()").value(12))
            .andExpect(jsonPath("$.data.months[10].revenueActual").value(sourceMonth.revenue().intValueExact()))
            .andExpect(jsonPath("$.data.months[10].operatingProfitActual").value(sourceMonth.operatingProfit().intValueExact()))
            .andExpect(jsonPath("$.data.summary.cumulativeRevenueAchievementRate").value(150.0))
            .andExpect(jsonPath("$.data.summary.cumulativeOperatingProfitAchievementRate").value(-50.0))
            .andExpect(jsonPath("$.data.months[11].revenueActual").isEmpty())
            .andExpect(jsonPath("$.data.months[11].revenueTarget").value(200));
        for (String id : List.of("식-00", "식-30", "식-36")) verify(calculation).definition(id);
        verify(source, never()).insertAccess(anyString(), anyString());
    }
    @ParameterizedTest @ValueSource(strings = {"202613", "202512", "202600", "20261"})
    void invalidMonthReadsNoBusinessData(String month) throws Exception {
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", month))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        verifyNoInteractions(source, targets, calculation);
    }
    @ParameterizedTest @ValueSource(strings = {"2025", "0", "10000", "abc"})
    void invalidYearIsFieldError(String year) throws Exception {
        mvc.perform(get(PATH).param("year", year).param("baseMonth", "202611"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("year"));
        verifyNoInteractions(source, targets);
    }
    @ParameterizedTest @ValueSource(strings = {"year", "baseMonth"})
    void missingRequiredQueryIsFieldError(String field) throws Exception {
        var request = get(PATH);
        if (!field.equals("year")) request.param("year", "2026");
        if (!field.equals("baseMonth")) request.param("baseMonth", "202611");
        mvc.perform(request).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field));
    }
    @Test void zeroTargetMonthStillContributesActualsToCumulativeNumerator() throws Exception {
        when(targets.targets(2026)).thenReturn(List.of(
            new Target("202601", n(0), n(0)), new Target("202602", n(200), n(100))));
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "202602"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.months[0].achievementRate").isEmpty())
            .andExpect(jsonPath("$.data.months[0].profitRateDifference").isEmpty())
            .andExpect(jsonPath("$.data.summary.cumulativeRevenueActual").value(600))
            .andExpect(jsonPath("$.data.summary.cumulativeRevenueTarget").value(200))
            .andExpect(jsonPath("$.data.summary.cumulativeRevenueAchievementRate").value(300.0));
    }
    @ParameterizedTest @ValueSource(longs = {0, -100})
    void nonpositiveCumulativeTargetSuppressesSummaryRatios(long target) throws Exception {
        when(targets.targets(2026)).thenReturn(List.of(new Target("202601", n(target), n(target))));
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "202601"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasTargets").value(true))
            .andExpect(jsonPath("$.data.summary.cumulativeRevenueAchievementRate").isEmpty())
            .andExpect(jsonPath("$.data.summary.cumulativeOperatingProfitAchievementRate").isEmpty());
    }
    // 식-36: 비율은 소수 첫째 자리 반올림(2026-11-24 사용자 결정, 예전 버림 −16.6 → −16.7).
    @Test void zeroProfitTargetWithPositiveRevenueKeepsMarginDifferenceRoundedHalfUp() throws Exception {
        when(targets.targets(2026)).thenReturn(List.of(new Target("202601", n(200), n(0))));
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "202601"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.months[0].profitRateDifference").value(-16.7));
    }
    @Test void absentAnnualTargetsKeepsActualAmountsButNoSummaryRatios() throws Exception {
        when(targets.targets(2026)).thenReturn(List.of());
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "202601"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasTargets").value(false))
            .andExpect(jsonPath("$.data.months[0].revenueActual").value(300))
            .andExpect(jsonPath("$.data.summary.cumulativeRevenueAchievementRate").isEmpty())
            .andExpect(jsonPath("$.data.summary.cumulativeOperatingProfitAchievementRate").isEmpty())
            .andExpect(jsonPath("$.data.summary.baseMonthRevenueAchievementRate").isEmpty());
    }
    @Test void unavailableActualIsNullAndMakesCumulativeActualIncomplete() throws Exception {
        when(source.snapshots(any())).thenReturn(List.of(saved("202601")));
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "202602"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.months[1].revenueActual").isEmpty())
            .andExpect(jsonPath("$.data.months[1].operatingProfitActual").isEmpty())
            .andExpect(jsonPath("$.data.summary.cumulativeRevenueActual").isEmpty())
            .andExpect(jsonPath("$.data.summary.cumulativeOperatingProfitAchievementRate").isEmpty());
    }
    @Test void closeMarkerUsesSamePeriodSourceAndGetNeverWritesAudit() throws Exception {
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "202610"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.baseMonthClosed").value(true));
        verify(source, never()).insertAccess(anyString(), anyString());
        verify(targets).targets(2026);
        verifyNoMoreInteractions(targets);
    }
    @Test void earlierBaseHidesLaterFixedActualAndPriorYearAllowsJanuaryAndDecember() throws Exception {
        when(periods.selectClosedMonths()).thenReturn(List.of("202612"));
        for (String month : List.of("202601", "202612")) {
            mvc.perform(get(PATH).param("year", "2026").param("baseMonth", month))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.baseMonth").value(month));
        }
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "202601"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.months[1].revenueActual").isEmpty());
    }
}
