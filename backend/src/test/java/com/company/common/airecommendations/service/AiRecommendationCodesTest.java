package com.company.common.airecommendations.service;

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

class AiRecommendationCodesTest {
    private final CodeGroupMapper mapper = mock(CodeGroupMapper.class);
    private final UserMapper management = mock(UserMapper.class);
    private final AiRecommendationCodes codes = new AiRecommendationCodes(new CommonCodeService(mapper, management));

    private CodeRows.Code row(String group, String code, String name, String use) {
        var row = new CodeRows.Code();
        row.groupId = group;
        row.code = code;
        row.codeName = name;
        row.useYn = use;
        row.delYn = "N";
        row.addVal1 = "DB 관계";
        return row;
    }

    private void group(String id, List<CodeRows.Code> rows) {
        var group = new CodeRows.Group();
        group.groupId = id;
        when(mapper.selectGroups()).thenReturn(List.of(group));
        when(mapper.selectCodes(id)).thenReturn(rows);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DATA_SCOPE", "CTRT_TYPE", "EMP_TYPE", "JOB", "GRADE", "JBPS", "INPUT_TYPE",
        "EXT_PSBL", "PROJ_STAT", "WORK_STAT", "SALES_STEP", "APRV_STAT", "LOSE_RSN", "RCMD_EXCL",
        "BANK", "INDUTY", "CLNT_TYPE", "DRCT_CLS", "ASGN_STAT", "OPP_STAT", "TENT_STAT",
        "RCMD_TYPE", "USER_ACT", "ACT", "SKILL"})
    void usesExistingDatabaseCodesIncludingRelationshipAttributes(String id) {
        group(id, List.of(row(id, "stored-code", "DB 표시명", "Y")));
        assertThat(codes.name(id, "stored-code")).isEqualTo("DB 표시명");
        assertThat(codes.selectable(id)).singleElement().satisfies(item -> {
            assertThat(item.code).isEqualTo("stored-code");
            if ("JOB".equals(id)) assertThat(item.relatedJobs).isEqualTo("DB 관계");
            if ("SKILL".equals(id)) assertThat(item.requiredJobs).isEqualTo("DB 관계");
        });
        verify(mapper, times(2)).selectCodes(id);
        verifyNoInteractions(management);
    }

    @Test
    void disabledCodeKeepsItsHistoricalNameButIsNotAnOption() {
        group("RCMD_EXCL", List.of(row("RCMD_EXCL", "old", "과거 사유", "N")));
        assertThat(codes.name("RCMD_EXCL", "old")).isEqualTo("과거 사유");
        assertThat(codes.selectable("RCMD_EXCL")).isEmpty();
        verifyNoInteractions(management);
    }

    @Test
    void emptyDatabaseDoesNotCreateExampleOptions() {
        group("JOB", List.of());
        assertThat(codes.selectable("JOB")).isEmpty();
        assertThatThrownBy(() -> codes.name("JOB", "BE")).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(management);
    }

    @Test
    void unknownGroupsDoNotReachTheDatabase() {
        assertThatThrownBy(() -> codes.selectable(null)).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> codes.selectable("UNKNOWN")).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper, management);
    }
}
