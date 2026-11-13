package com.company.common.service;

import com.company.common.dto.CodeMapContracts.DeleteInput;
import com.company.common.dto.CodeMapContracts.Item;
import com.company.common.mapper.CommonCodeMapMapper;
import com.company.common.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommonCodeMapServiceTest {
    final CommonCodeMapMapper mapper = mock(CommonCodeMapMapper.class);
    final UserMapper management = mock(UserMapper.class);
    final CommonCodeMapService service = new CommonCodeMapService(mapper, management);
    final Item item = new Item("정직원", "EMP_TYPE", "FULL");

    @Test void databaseUniqueRaceBecomesTheContract400FailureWithoutSuccessHistory() {
        when(mapper.selectableCode(item)).thenReturn(true);
        when(mapper.insertMap(any())).thenThrow(new DuplicateKeyException("unique violation"));
        assertThatThrownBy(() -> service.postCommonCodeMap(item))
            .isInstanceOf(CodeFailure.class).hasMessageContaining("이미 등록");
        verify(management, never()).insertHistory(any());
    }
    @Test void missingGeneratedKeyNeverCreatesFabricatedSuccess() {
        when(mapper.selectableCode(item)).thenReturn(true);
        when(mapper.insertMap(any())).thenReturn(1);
        assertThatThrownBy(() -> service.postCommonCodeMap(item)).isInstanceOf(IllegalStateException.class);
        verify(management, never()).insertHistory(any());
    }
    @Test void invalidDirectServiceCallCannotBypassFieldValidation() {
        assertThatThrownBy(() -> service.postCommonCodeMap(new Item("", "EMP_TYPE", "FULL")))
            .isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper, management);
    }
    @Test void emptyDirectDeleteCannotBypassValidation() {
        assertThatThrownBy(() -> service.postCommonCodeMapDelete(new DeleteInput(List.of())))
            .isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper, management);
    }
}
