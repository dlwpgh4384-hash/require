package com.company.common.common;

import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.mapper.CodeRows;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.users.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceItemCodeSourceTest {
    private final CodeGroupMapper mapper = mock(CodeGroupMapper.class);
    private final UserMapper management = mock(UserMapper.class);
    private final CommonCodeService codes = new CommonCodeService(mapper, management);

    @ParameterizedTest
    @ValueSource(strings = {"DSTRB_STEP", "SGA_ITEM", "FIN_CLS", "FORM_TYPE", "UPLD_STAT",
        "ADJ_ITEM", "PAY_TYPE", "IO", "XFER_CLS"})
    void optionsUseExistingCodeValuesAndCurrentDatabaseLabels(String groupId) {
        var group = new CodeRows.Group();
        group.groupId = groupId;
        when(mapper.selectGroups()).thenReturn(List.of(group));
        var row = new CodeRows.Code();
        row.groupId = groupId;
        row.code = "DB_VALUE";
        row.codeName = "기존 원천 이름";
        row.useYn = "Y";
        row.delYn = "N";
        when(mapper.selectCodes(groupId)).thenReturn(List.of(row));
        var before = codes.getCommonCodes(groupId).items();
        assertThat(before).extracting(item -> item.code).containsExactly(row.code);
        assertThat(before).extracting(item -> item.codeName).containsExactly("기존 원천 이름");
        row.codeName = "변경 원천 이름";
        assertThat(codes.getCommonCodes(groupId).items()).extracting(item -> item.codeName)
            .containsExactly("변경 원천 이름");
        assertThat(before).extracting(item -> item.codeName).containsExactly("기존 원천 이름");
        verify(mapper, times(2)).selectGroups();
        verify(mapper, times(2)).selectCodes(groupId);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(management);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DSTRB_STEP", "SGA_ITEM", "FIN_CLS", "FORM_TYPE", "UPLD_STAT",
        "ADJ_ITEM", "PAY_TYPE", "IO", "XFER_CLS"})
    void emptyCodeSourceDoesNotInventSpecimenOptions(String groupId) {
        var group = new CodeRows.Group();
        group.groupId = groupId;
        when(mapper.selectGroups()).thenReturn(List.of(group));
        when(mapper.selectCodes(groupId)).thenReturn(List.of());
        assertThat(codes.getCommonCodes(groupId).items()).isEmpty();
        verify(mapper).selectGroups();
        verify(mapper).selectCodes(groupId);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(management);
    }
}
