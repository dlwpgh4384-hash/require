package com.company.common.airecommendations.controller;

import com.company.common.codegroups.dto.CodeContracts.Item;
import com.company.common.airecommendations.service.AiRecommendationCodes;
import com.company.common.airecommendations.dto.AiRecommendationOptionsContracts.Filter;
import com.company.common.airecommendations.mapper.AiRecommendationOptionsMapper;
import com.company.common.airecommendations.mapper.AiRecommendationOptionsMapper.*;
import com.company.common.airecommendations.service.AiRecommendationOptionsService;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.BusinessCalendar;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.company.common.support.controller.OpenApiResponseContract.response;

@WebMvcTest(AiRecommendationOptionsController.class)
@ContextConfiguration(classes = {AiRecommendationOptionsController.class, AiRecommendationOptionsService.class,
    BusinessCalendar.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class AiRecommendationOptionsControllerTest {
    private static final String PATH = "/api/common/ai-recommendation-options";
    @Autowired MockMvc mvc;
    @MockBean AiRecommendationOptionsMapper mapper;
    @MockBean AiRecommendationCodes codes;

    @BeforeEach void setup() {
        when(codes.selectable("JOB")).thenReturn(List.of(choice("BE", "백엔드")));
        when(codes.selectable("GRADE")).thenReturn(List.of(choice("SR", "고급")));
        when(codes.selectable("RCMD_EXCL")).thenReturn(List.of(choice("SCHEDULE", "일정이 맞지 않음")));
        when(mapper.targets(any())).thenReturn(List.of(
            new TargetRow("project", "same-id", "가 프로젝트", "202611", "202712"),
            new TargetRow("opportunity", "same-id", "나 기회", "202612", null),
            new TargetRow("opportunity", "empty", "다 빈 기회", "202701", null)));
        when(mapper.needs(List.of("same-id", "empty"))).thenReturn(List.of(
            new NeedRow("same-id", 2, "BE", "백엔드", 3, "202612"),
            new NeedRow("same-id", 7, "BE", "백엔드", 1, "202701")));
    }

    private Item choice(String code, String name) {
        var item = new Item();
        item.code = code;
        item.codeName = name;
        item.useYn = "Y";
        return item;
    }

    @Test void publicReadPreservesOrderingMonthsStableNeedNumbersAndCodeLabels() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(response("GET", PATH))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.targets[0].targetName").value("가 프로젝트"))
            .andExpect(jsonPath("$.data.targets[0].startMonth").value("2026-11"))
            .andExpect(jsonPath("$.data.targets[0].endMonth").value("2027-12"))
            .andExpect(jsonPath("$.data.targets[0].needs").isEmpty())
            .andExpect(jsonPath("$.data.targets[1].endMonth").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.targets[1].needs[0].needSn").value(2))
            .andExpect(jsonPath("$.data.targets[1].needs[1].needSn").value(7))
            .andExpect(jsonPath("$.data.targets[1].needs[0].requiredCount").value(3))
            .andExpect(jsonPath("$.data.targets[1].needs[0].jobName").value("백엔드"))
            .andExpect(jsonPath("$.data.targets[1].needs[1].startMonth").value("2027-01"))
            .andExpect(jsonPath("$.data.targets[2].needs").isEmpty())
            .andExpect(jsonPath("$.data.jobs[0].name").value("백엔드"))
            .andExpect(jsonPath("$.data.grades[0].code").value("SR"))
            .andExpect(jsonPath("$.data.exclusionReasons[0].name").value("일정이 맞지 않음"));
        verify(mapper).targets(new Filter(null, null));
        verify(mapper, times(1)).needs(List.of("same-id", "empty"));
        verifyNoMoreInteractions(mapper);
    }

    @Test void selectedTargetFiltersArePassedWithoutBrowserQueryHandoff() throws Exception {
        when(mapper.targets(new Filter("opportunity", "chosen"))).thenReturn(List.of(
            new TargetRow("opportunity", "chosen", "선택 기회", "202612", null)));
        when(mapper.needs(List.of("chosen"))).thenReturn(List.of());
        mvc.perform(get(PATH).param("targetType", "opportunity").param("targetId", "chosen"))
            .andExpect(status().isOk()).andExpect(response("GET", PATH))
            .andExpect(jsonPath("$.data.targets.length()").value(1))
            .andExpect(jsonPath("$.data.targets[0].needs").isEmpty());
        verify(mapper).targets(new Filter("opportunity", "chosen"));
    }

    @Test void typeOnlyFilterIsOptional() throws Exception {
        when(mapper.targets(new Filter("project", null))).thenReturn(List.of(
            new TargetRow("project", "chosen", "선택 프로젝트", "202612", "202801")));
        mvc.perform(get(PATH).param("targetType", "project")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.targets[0].targetType").value("project"));
        verify(mapper, never()).needs(any());
    }

    @Test void idOnlyFilterIsOptional() throws Exception {
        when(mapper.targets(new Filter(null, "chosen"))).thenReturn(List.of(
            new TargetRow("project", "chosen", "선택 프로젝트", "202612", "202801")));
        mvc.perform(get(PATH).param("targetId", "chosen")).andExpect(status().isOk());
        verify(mapper).targets(new Filter(null, "chosen"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "PROJECT", "internal", "unknown"})
    void invalidTargetTypeHasFieldErrorBeforeDatabaseRead(String type) throws Exception {
        mvc.perform(get(PATH).param("targetType", type)).andExpect(status().isBadRequest())
            .andExpect(response("GET", PATH)).andExpect(jsonPath("$.errors[0].field").value("targetType"));
        verifyNoInteractions(mapper, codes);
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "identifier-longer-than-twenty"})
    void invalidTargetIdHasFieldErrorBeforeDatabaseRead(String id) throws Exception {
        mvc.perform(get(PATH).param("targetId", id)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("targetId"));
        verifyNoInteractions(mapper, codes);
    }

    @Test void missingOrNoLongerSelectableTargetIs400NotAnEmptySuccessfulSelection() throws Exception {
        when(mapper.targets(new Filter("project", "closed"))).thenReturn(List.of());
        mvc.perform(get(PATH).param("targetType", "project").param("targetId", "closed"))
            .andExpect(status().isBadRequest()).andExpect(response("GET", PATH))
            .andExpect(jsonPath("$.errors[0].field").value("targetId"));
        verify(mapper, never()).needs(any());
        verifyNoInteractions(codes);
    }

    @Test void noOngoingTargetsReturnsRealEmptyOptionsWithoutInventingNeeds() throws Exception {
        when(mapper.targets(any())).thenReturn(List.of());
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(response("GET", PATH))
            .andExpect(jsonPath("$.data.targets").isEmpty())
            .andExpect(jsonPath("$.data.jobs[0].code").value("BE"));
        verify(mapper, never()).needs(any());
    }
}
