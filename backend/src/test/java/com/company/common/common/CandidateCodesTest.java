package com.company.common.common;

import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.mapper.CodeRows;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CandidateCodesTest {
    private final CodeGroupMapper mapper = mock(CodeGroupMapper.class);
    private final UserMapper management = mock(UserMapper.class);
    private final CandidateCodes codes = new CandidateCodes(new CommonCodeService(mapper, management));

    private CodeRows.Code row(String group, String code, String name, String use) {
        var row = new CodeRows.Code();
        row.groupId = group;
        row.code = code;
        row.codeName = name;
        row.useYn = use;
        row.delYn = "N";
        return row;
    }

    private void group(String id, List<CodeRows.Code> rows) {
        var group = new CodeRows.Group();
        group.groupId = id;
        when(mapper.selectGroups()).thenReturn(List.of(group));
        when(mapper.selectCodes(id)).thenReturn(rows);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMP_TYPE", "JOB", "GRADE", "CNTC_STAT", "CAND_SRC", "SRC"})
    void namesAndOptionsUseExistingDatabaseCodesWithoutNewEnums(String id) {
        group(id, List.of(row(id, "stored-code", "DB 표시명", "Y")));
        assertThat(codes.name(id, "stored-code")).isEqualTo("DB 표시명");
        assertThat(codes.selectable(id)).extracting(item -> item.code).containsExactly("stored-code");
        verify(mapper, times(2)).selectCodes(id);
        verifyNoInteractions(management);
    }

    @Test
    void disabledHistoricalNameIsVisibleButNotSelectable() {
        var disabled = row("JOB", "OLD", "과거 이름", "N");
        var enabled = row("JOB", "NEW", "현재 이름", "Y");
        group("JOB", List.of(disabled, enabled));
        assertThat(codes.name("JOB", "OLD")).isEqualTo("과거 이름");
        assertThat(codes.selectable("JOB")).extracting(item -> item.code).containsExactly("NEW");
        disabled.codeName = "바뀐 이름";
        enabled.useYn = "N";
        assertThat(codes.name("JOB", "OLD")).isEqualTo("바뀐 이름");
        assertThat(codes.selectable("JOB")).isEmpty();
        verifyNoInteractions(management);
    }

    @Test
    void absentCodeHasNoHardcodedFallback() {
        group("CNTC_STAT", List.of());
        assertThatThrownBy(() -> codes.name("CNTC_STAT", "BEFORE"))
            .isInstanceOfSatisfying(CodeFailure.class, error -> assertThat(error.getField()).isEqualTo("code"));
        verifyNoInteractions(management);
    }

    @Test
    void unknownOrNullGroupIsRejectedBeforeDatabaseRead() {
        assertThatThrownBy(() -> codes.name(null, "code")).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> codes.selectable("UNKNOWN")).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper, management);
    }
}
