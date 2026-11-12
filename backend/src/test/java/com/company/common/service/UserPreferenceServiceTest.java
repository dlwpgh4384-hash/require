package com.company.common.service;

import com.company.common.mapper.UserPreferenceMapper;
import com.company.common.mapper.UserPreferenceRow;
import com.company.common.dto.UserPreferenceInput;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserPreferenceServiceTest {
    private final UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
    private final CommonUserPreferenceService service = new CommonUserPreferenceService(mapper,
        new UserPreferenceRules(new ObjectMapper()), mock(UserPreferencePeriodBasis.class));

    @Test
    void invalidRequestStopsBeforeAnyPersistenceAccess() {
        assertThatThrownBy(() -> service.putCommonUserPreference("PAGE_SIZE", "GLOBAL", new UserPreferenceInput("30", null)))
            .isInstanceOf(UserPreferenceFailure.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void existingNullTimestampIsAnUpdateNotAnInsert() {
        var row = new UserPreferenceRow();
        row.setPrefVal("false");
        when(mapper.selectPreference("MENU_FOLD", "GLOBAL")).thenReturn(row);
        when(mapper.updatePreference("MENU_FOLD", "GLOBAL", "true", null)).thenReturn(1);
        service.putCommonUserPreference("MENU_FOLD", "GLOBAL", new UserPreferenceInput("true", null));
        verify(mapper).updatePreference("MENU_FOLD", "GLOBAL", "true", null);
        verify(mapper, never()).insertPreference(anyString(), anyString(), anyString());
    }

    @Test
    void deletedTargetWithNonNullTokenDoesNotBecomeAnInsert() {
        assertThatThrownBy(() -> service.putCommonUserPreference("DENSE", "GLOBAL",
            new UserPreferenceInput("넓게", java.time.Instant.parse("2026-01-01T00:00:00.000000Z"))))
            .hasMessage(UserPreferenceFailure.CONFLICT);
        verify(mapper, never()).insertPreference(anyString(), anyString(), anyString());
    }
}
