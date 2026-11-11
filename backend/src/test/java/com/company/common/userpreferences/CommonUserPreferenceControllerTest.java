package com.company.common.userpreferences;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonUserPreferenceController.class)
@ContextConfiguration(classes = {CommonUserPreferenceController.class, CommonUserPreferenceService.class,
    UserPreferenceRules.class, UserPreferenceExceptionHandler.class})
class CommonUserPreferenceControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean UserPreferenceMapper mapper;
    @MockBean UserPreferencePeriodBasis periodBasis;
    private static final LocalDateTime TOKEN = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123456000);

    @BeforeEach
    void readDurableOpenApiContract() throws Exception {
        String contract = new ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("/api/common/user-preferences/{prefTypeCd}/{prefKey}:",
            "operationId: getCommonUserPreference", "operationId: putCommonUserPreference");
    }

    static UserPreferenceRow row(String value, LocalDateTime token) {
        var row = new UserPreferenceRow();
        row.setPrefVal(value);
        row.setModDttm(token);
        return row;
    }

    @Test
    void happyExistingPageSizeIsSavedAndReturnedByFollowingGet() throws Exception {
        when(mapper.selectPreference("PAGE_SIZE", "GLOBAL"))
            .thenReturn(row("20", TOKEN), row("50", TOKEN.plusNanos(1000)), row("50", TOKEN.plusNanos(1000)));
        when(mapper.updatePreference("PAGE_SIZE", "GLOBAL", "50", TOKEN)).thenReturn(1);
        mvc.perform(put("/api/common/user-preferences/PAGE_SIZE/GLOBAL").contentType("application/json")
            .content("{\"prefVal\":\"50\",\"modDttm\":\"2026-01-02T03:04:05.123456Z\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.prefVal").value("50"))
            .andExpect(jsonPath("$.data.modDttm").value("2026-01-02T03:04:05.123457Z"));
        mvc.perform(get("/api/common/user-preferences/PAGE_SIZE/GLOBAL")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.prefVal").value("50"));
        verify(mapper).updatePreference("PAGE_SIZE", "GLOBAL", "50", TOKEN);
    }

    @Test
    void authAnonymousMenuFoldWithExistingNullTokenIsAllowed() throws Exception {
        when(mapper.selectPreference("MENU_FOLD", "GLOBAL")).thenReturn(row("false", null), row("true", TOKEN));
        when(mapper.updatePreference("MENU_FOLD", "GLOBAL", "true", null)).thenReturn(1);
        mvc.perform(put("/api/common/user-preferences/MENU_FOLD/GLOBAL").contentType("application/json")
            .content("{\"prefVal\":\"true\",\"modDttm\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.prefVal").value("true"));
        mvc.perform(get("/api/common/user-preferences/MENU_FOLD/GLOBAL")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.modDttm").value("2026-01-02T03:04:05.123456Z"));
        verify(mapper, never()).insertPreference(anyString(), anyString(), anyString());
    }

    @Test
    void missingRowIsNotSeeded() throws Exception {
        mvc.perform(get("/api/common/user-preferences/PAGE_SIZE/GLOBAL")).andExpect(status().isOk())
            .andExpect(content().json("""
                {"success":true,"meta":{},"data":{"prefTypeCd":"PAGE_SIZE","prefKey":"GLOBAL",
                 "found":false,"prefVal":null,"modDttm":null}}
                """, true));
        verify(mapper).selectPreference("PAGE_SIZE", "GLOBAL");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void validationInvalidPageSizeHasPrefValErrorAndDoesNotWrite() throws Exception {
        when(mapper.selectPreference("PAGE_SIZE", "GLOBAL")).thenReturn(row("20", TOKEN));
        mvc.perform(put("/api/common/user-preferences/PAGE_SIZE/GLOBAL").contentType("application/json")
            .content("{\"prefVal\":\"30\",\"modDttm\":null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors[0].field").value("prefVal"));
        verifyNoInteractions(mapper);
        mvc.perform(get("/api/common/user-preferences/PAGE_SIZE/GLOBAL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.prefVal").value("20"))
            .andExpect(jsonPath("$.data.modDttm").value("2026-01-02T03:04:05.123456Z"));
        verify(mapper).selectPreference("PAGE_SIZE", "GLOBAL");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void businessStaleTimestampDoesNotInsertOrOverwrite() throws Exception {
        when(mapper.selectPreference("PAGE_SIZE", "GLOBAL")).thenReturn(row("100", TOKEN));
        when(mapper.updatePreference("PAGE_SIZE", "GLOBAL", "50", null)).thenReturn(0);
        mvc.perform(put("/api/common/user-preferences/PAGE_SIZE/GLOBAL").contentType("application/json")
            .content("{\"prefVal\":\"50\",\"modDttm\":null}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(UserPreferenceFailure.CONFLICT));
        mvc.perform(get("/api/common/user-preferences/PAGE_SIZE/GLOBAL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.prefVal").value("100"))
            .andExpect(jsonPath("$.data.modDttm").value("2026-01-02T03:04:05.123456Z"));
        verify(mapper).updatePreference("PAGE_SIZE", "GLOBAL", "50", null);
        verify(mapper, never()).insertPreference(anyString(), anyString(), anyString());
    }

    @Test
    void concurrentInsertBecomesConflictWithoutReadAfterFailedTransaction() throws Exception {
        when(mapper.insertPreference("DENSE", "GLOBAL", "좁게")).thenThrow(new DuplicateKeyException("SQL private"));
        mvc.perform(put("/api/common/user-preferences/DENSE/GLOBAL").contentType("application/json")
            .content("{\"prefVal\":\"좁게\",\"modDttm\":null}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(UserPreferenceFailure.CONFLICT));
        verify(mapper, times(1)).selectPreference("DENSE", "GLOBAL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"prefVal\":\"20\"}",
        "{\"prefVal\":20,\"modDttm\":null}", "{\"prefVal\":\"20\",\"modDttm\":\"bad\"}",
        "{\"prefVal\":\"20\",\"modDttm\":null,\"userId\":\"other\"}", "{"})
    void malformedOrNonContractBodyIs400(String body) throws Exception {
        mvc.perform(put("/api/common/user-preferences/PAGE_SIZE/GLOBAL").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors[0].field").exists());
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/common/user-preferences/OTHER/GLOBAL",
        "/api/common/user-preferences/PAGE_SIZE/OTHER",
        "/api/common/user-preferences/PERIOD/too-long-screen-key"})
    void invalidPathIs400(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors[0].field").exists());
        verifyNoInteractions(mapper);
    }

    @Test
    void operationalFailureIsSanitizedJson500() throws Exception {
        when(mapper.selectPreference("DENSE", "GLOBAL"))
            .thenThrow(new DataAccessResourceFailureException("private SQL password"));
        mvc.perform(get("/api/common/user-preferences/DENSE/GLOBAL")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("설정을 저장하거나 불러오지 못했습니다. 다시 시도해 주세요."));
    }

    @Test
    void periodReadKeepsMissingPreferenceAndAddsBasisOnlyToMetadata() throws Exception {
        when(periodBasis.metadata()).thenReturn(Map.of("periodBasis",
            new UserPreferencePeriodBasis.Basis("202701", "202601", java.util.List.of("202612"))));
        mvc.perform(get("/api/common/user-preferences/PERIOD/UI-16")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.found").value(false))
            .andExpect(jsonPath("$.data.prefVal").isEmpty())
            .andExpect(jsonPath("$.meta.periodBasis.latestYm").value("202701"));
        verify(mapper).selectPreference("PERIOD", "UI-16");
        verifyNoMoreInteractions(mapper);
    }
}
