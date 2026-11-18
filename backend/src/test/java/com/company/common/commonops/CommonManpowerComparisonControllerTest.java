package com.company.common.commonops;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.manpowercomparisons.controller.CommonManpowerComparisonController;
import com.company.common.manpowercomparisons.dto.ManpowerComparisonContracts.Filter;
import com.company.common.manpowercomparisons.mapper.ManpowerComparisonMapper;
import com.company.common.manpowercomparisons.mapper.ManpowerComparisonRows.Source;
import com.company.common.manpowercomparisons.service.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.controller.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonManpowerComparisonController.class)
@ContextConfiguration(classes = {CommonManpowerComparisonController.class, CommonManpowerComparisonService.class,
    ManpowerComparisonRules.class, CalculationRuleService.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonManpowerComparisonControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ManpowerComparisonMapper mapper;
    @MockBean CalculationRuleMapper definitions;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean PlatformTransactionManager transactions;
    static final String PATH = "/api/common/manpower-comparisons";

    @BeforeEach void setup() throws Exception {
        ManpowerComparisonDefinitions.load(definitions);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(mapper.selectSources(any())).thenReturn(List.of(
            source("P-B", "BE", "0", "2"), source("P-A", "FE", "3", "0"), source("P-A", "BE", "0", "0")));
    }
    Source source(String project, String job, String plan, String actual) {
        return new Source(project, "프로젝트 " + project, job, job, LocalDate.of(2026,1,1), LocalDate.of(2026,12,31),
            new BigDecimal(plan), new BigDecimal(actual));
    }

    @Test void happyUnionMissingSidesOrderingAndTotalsUseRealService() throws Exception {
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.data.items[0].projectName").isString())
            .andExpect(jsonPath("$.data.items[0].jobName").isString())
            .andExpect(jsonPath("$.data.items[0].plannedMm").isNumber())
            .andExpect(jsonPath("$.data.items[0].differenceMm").isNumber())
            .andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.items[0].projectId").value("P-A"))
            .andExpect(jsonPath("$.data.items[0].jobCode").value("FE"))
            .andExpect(jsonPath("$.data.items[0].actualMm").value(0))
            .andExpect(jsonPath("$.data.items[1].differenceStatus").value("맞음"))
            .andExpect(jsonPath("$.data.items[2].plannedMm").value(0))
            .andExpect(jsonPath("$.data.totals.plannedMm").value(3))
            .andExpect(jsonPath("$.data.totals.actualMm").value(2))
            .andExpect(jsonPath("$.data.totals.differenceMm").value(-1));
    }

    @Test void anonymousNeedsNoSessionOrIdentityHeaders() throws Exception {
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items").isArray()).andExpect(header().doesNotExist("Location"))
            .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test void missingYearHasFieldError() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("year"));
        verifyNoInteractions(mapper, definitions);
    }

    @ParameterizedTest @ValueSource(strings={"2024", "2028", "wrong", "", "2147483648"})
    void invalidYearHasFieldError(String year) throws Exception {
        mvc.perform(get(PATH).param("year", year)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("year"));
        verifyNoInteractions(mapper, definitions);
    }

    @ParameterizedTest @ValueSource(strings={"미정", "SHORT", "", " 부족"})
    void invalidStatusHasFieldError(String status) throws Exception {
        mvc.perform(get(PATH).param("year", "2026").param("differenceStatus", status))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("differenceStatus"));
        verifyNoInteractions(mapper, definitions);
    }

    @ParameterizedTest @ValueSource(strings={"", " ", "123456789012345678901", "invalid\n"})
    void invalidProjectUsesExistingIdentifierPolicy(String id) throws Exception {
        mvc.perform(get(PATH).param("year", "2026").param("projectId", id)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("projectId"));
        verifyNoInteractions(mapper, definitions);
    }

    @Test void totalsFollowDifferenceFilterAndTieOrderIsIdentifierBased() throws Exception {
        when(mapper.selectSources(any())).thenReturn(List.of(
            source("P-B", "BE", "1", "0"), source("P-A", "FE", "1", "0"),
            source("P-A", "BE", "1", "0"), source("P-A", "PM", "0", "3")));
        mvc.perform(get(PATH).param("year", "2026").param("differenceStatus", "부족"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.items[0].jobCode").value("BE"))
            .andExpect(jsonPath("$.data.items[1].jobCode").value("FE"))
            .andExpect(jsonPath("$.data.items[2].projectId").value("P-B"))
            .andExpect(jsonPath("$.data.totals.plannedMm").value(3))
            .andExpect(jsonPath("$.data.totals.actualMm").value(0))
            .andExpect(jsonPath("$.data.totals.differenceMm").value(-3));
    }

    @Test void unknownValidProjectReturnsEmptyAndNumericZeros() throws Exception {
        when(mapper.selectSources(new Filter(2026,"unknown",null))).thenReturn(List.of());
        mvc.perform(get(PATH).param("year", "2026").param("projectId", "unknown")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.totals.plannedMm").value(0))
            .andExpect(jsonPath("$.data.totals.actualMm").value(0))
            .andExpect(jsonPath("$.data.totals.differenceMm").value(0));
        verify(mapper).selectSources(new Filter(2026,"unknown",null));
    }

    @Test void readFailureIsNotDisguisedAsSuccessfulEmpty() throws Exception {
        when(mapper.selectSources(any())).thenThrow(new IllegalStateException("private SQL"));
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test void annualRoundingPrecedesStatusFilterAndTotals() throws Exception {
        when(mapper.selectSources(any())).thenReturn(List.of(new Source(
            "P-ANNUAL", "여러 해 프로젝트", "BE", "백엔드",
            LocalDate.of(2025, 12, 31), LocalDate.of(2026, 2, 1),
            BigDecimal.ONE, new BigDecimal("0.67"))));
        mvc.perform(get(PATH).param("year", "2026").param("differenceStatus", "맞음"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].plannedMm").value(0.67))
            .andExpect(jsonPath("$.data.items[0].differenceMm").value(0))
            .andExpect(jsonPath("$.data.totals.plannedMm").value(0.67))
            .andExpect(jsonPath("$.data.totals.actualMm").value(0.67));
    }

    @Test void eachRequestReadsCurrentDefinitionsRatherThanCachingThem() throws Exception {
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isOk());
        clearInvocations(mapper, definitions);
        definitions.selectDefinition("식-67").formula = "차이 = 계획 MM − 실제·예정 MM";
        clearInvocations(definitions);
        mvc.perform(get(PATH).param("year", "2026"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist());
        for (String id : List.of("00", "01", "62", "67"))
            verify(definitions).selectDefinition("식-" + id);
        verifyNoInteractions(mapper);
    }

    @Test void incompatibleDefinitionsAreAnErrorEvenWhenSourcesWouldBeEmpty() throws Exception {
        when(mapper.selectSources(any())).thenReturn(List.of());
        definitions.selectDefinition("식-67").details = "계획 MM은 반올림하지 않는다.";
        mvc.perform(get(PATH).param("year", "2026"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(mapper);
    }
}
