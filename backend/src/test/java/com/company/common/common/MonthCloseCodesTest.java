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

class MonthCloseCodesTest {
    private final CodeGroupMapper mapper = mock(CodeGroupMapper.class);
    private final UserMapper management = mock(UserMapper.class);
    private final MonthCloseCodes codes = new MonthCloseCodes(new CommonCodeService(mapper, management));

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
    @ValueSource(strings = {"CTRT_TYPE", "EMP_TYPE", "JOB", "INPUT_TYPE", "EXT_PSBL", "SGA_ITEM",
        "FIN_CLS", "CLOSE_STAT", "DRCT_CLS", "ASGN_STAT", "COST_CLS", "SETL_STAT", "ACT",
        "CHK_ITEM", "CHK_STAT"})
    void namesComeFromExistingCodeService(String id) {
        group(id, List.of(row(id, "stored-code", "DB 표시명", "Y")));
        assertThat(codes.name(id, "stored-code")).isEqualTo("DB 표시명");
        verify(mapper).selectGroups();
        verify(mapper).selectCodes(id);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(management);
    }

    @Test
    void closeStatusAndHistoryActionAreIndependentDatabaseDomains() {
        group("CLOSE_STAT", List.of(row("CLOSE_STAT", "WORK", "작업중", "Y"),
            row("CLOSE_STAT", "CLOSE", "마감", "Y")));
        assertThat(codes.name("CLOSE_STAT", "WORK")).isEqualTo("작업중");
        assertThat(codes.name("CLOSE_STAT", "CLOSE")).isEqualTo("마감");
        assertThatThrownBy(() -> codes.name("CLOSE_STAT", "OPEN")).isInstanceOf(CodeFailure.class);
        group("ACT", List.of(row("ACT", "CLOSE", "마감", "Y"), row("ACT", "OPEN", "해제", "Y")));
        assertThat(codes.name("ACT", "OPEN")).isEqualTo("해제");
        assertThatThrownBy(() -> codes.name("ACT", "WORK")).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(management);
    }

    @Test
    void historicalDisabledLabelRemainsVisible() {
        var stored = row("ACT", "OPEN", "해제", "N");
        group("ACT", List.of(stored));
        assertThat(codes.name("ACT", "OPEN")).isEqualTo("해제");
        stored.codeName = "변경된 DB 표시명";
        assertThat(codes.name("ACT", "OPEN")).isEqualTo("변경된 DB 표시명");
        verifyNoInteractions(management);
    }

    @Test
    void absentCodeIsNotReplacedByHardcodedLabel() {
        group("CHK_STAT", List.of());
        assertThatThrownBy(() -> codes.name("CHK_STAT", "WARN"))
            .isInstanceOfSatisfying(CodeFailure.class, error -> assertThat(error.getField()).isEqualTo("code"));
        verifyNoInteractions(management);
    }

    @Test
    void unapprovedGroupIsRejectedWithoutDatabaseAccess() {
        assertThatThrownBy(() -> codes.name(null, "CLOSE")).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> codes.name("UNKNOWN", "CLOSE")).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper, management);
    }
}
