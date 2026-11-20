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

class ProjectCostAnalysisCodeSourceTest {
    private final CodeGroupMapper mapper = mock(CodeGroupMapper.class);
    private final UserMapper management = mock(UserMapper.class);
    private final CommonCodeService codes = new CommonCodeService(mapper, management);

    @ParameterizedTest
    @ValueSource(strings = {"DATA_SCOPE", "CTRT_TYPE", "JOB", "INPUT_TYPE", "DSTRB_STEP",
        "EXT_PSBL", "DRCT_EXPS_ITEM", "CHG_ITEM", "PROJ_STAT", "ASGN_CHG_RSN",
        "LOSE_RSN", "CHG_TYPE", "SRC", "ROW_TYPE", "ASGN_STAT", "ASGN_CHG_TYPE", "COST_CLS"})
    void requestedGroupsUseExistingCodeReadAndReturnChangedSourceNames(String groupId) {
        var group = new CodeRows.Group();
        group.groupId = groupId;
        when(mapper.selectGroups()).thenReturn(List.of(group));
        var row = new CodeRows.Code();
        row.groupId = groupId;
        row.code = "SOURCE";
        row.codeName = "DB 원천 표시명";
        row.useYn = "Y";
        row.delYn = "N";
        when(mapper.selectCodes(groupId)).thenReturn(List.of(row));

        var before = codes.getCommonCodes(groupId).items();
        assertThat(before).extracting(item -> item.code).containsExactly(row.code);
        assertThat(before).extracting(item -> item.codeName).containsExactly("DB 원천 표시명");
        row.codeName = "DB 변경 표시명";
        var after = codes.getCommonCodes(groupId).items();
        assertThat(after).extracting(item -> item.codeName).containsExactly("DB 변경 표시명");
        assertThat(before).extracting(item -> item.codeName).containsExactly("DB 원천 표시명");

        verify(mapper, times(2)).selectGroups();
        verify(mapper, times(2)).selectCodes(groupId);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(management);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CTRT_TYPE", "PROJ_STAT", "JOB"})
    void existingEmptyGroupReturnsNoInventedOptions(String groupId) {
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
