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

class SalesPipelineCodesTest {
    private final CodeGroupMapper mapper = mock(CodeGroupMapper.class);
    private final UserMapper management = mock(UserMapper.class);
    private final SalesPipelineCodes codes = new SalesPipelineCodes(new CommonCodeService(mapper, management));

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
    @ValueSource(strings = {"NOTI_TYPE", "SEND_MTHD", "CTRT_TYPE", "EMP_TYPE", "JOB", "GRADE", "JBPS",
        "INPUT_TYPE", "EXT_PSBL", "PROJ_STAT", "WORK_STAT", "SALES_STEP", "APRV_STAT", "CNTC_STAT",
        "CAND_SRC", "LOSE_RSN", "DRCT_CLS", "SRC", "ASGN_STAT", "OPP_STAT", "TENT_STAT", "APRV_ACT", "ACT"})
    void everyRequiredGroupUsesStoredCodesAndDisplayNames(String id) {
        group(id, List.of(row(id, "stored-code", "DB 표시명", "Y")));
        assertThat(codes.name(id, "stored-code")).isEqualTo("DB 표시명");
        assertThat(codes.selectable(id)).extracting(item -> item.code).containsExactly("stored-code");
        verify(mapper, times(2)).selectCodes(id);
        verifyNoInteractions(management);
    }

    @Test
    void inactiveHistoricalCodeRetainsNameButCannotBeSelected() {
        var inactive = row("JOB", "OLD", "과거 이름", "N");
        var active = row("JOB", "NEW", "현재 이름", "Y");
        group("JOB", List.of(inactive, active));
        assertThat(codes.name("JOB", "OLD")).isEqualTo("과거 이름");
        assertThat(codes.selectable("JOB")).extracting(item -> item.code).containsExactly("NEW");
        active.useYn = "N";
        inactive.codeName = "수정 이름";
        assertThat(codes.selectable("JOB")).isEmpty();
        assertThat(codes.name("JOB", "OLD")).isEqualTo("수정 이름");
    }

    @Test
    void jobPriceUsesExistingAddValueMapping() {
        var job = row("JOB", "stored-job", "DB 직무", "Y");
        job.addVal2 = "7123456";
        group("JOB", List.of(job));
        assertThat(codes.selectable("JOB").get(0).estimateUnitPrice).isEqualByComparingTo("7123456");
        job.addVal2 = "8234567";
        assertThat(codes.selectable("JOB").get(0).estimateUnitPrice).isEqualByComparingTo("8234567");
    }

    @Test
    void missingCodeCannotUseNewEnumOrFallbackName() {
        group("OPP_STAT", List.of());
        assertThatThrownBy(() -> codes.name("OPP_STAT", "ING"))
            .isInstanceOfSatisfying(CodeFailure.class, error -> assertThat(error.getField()).isEqualTo("code"));
        verifyNoInteractions(management);
    }

    @Test
    void unknownOrNullGroupIsRejectedBeforeReading() {
        assertThatThrownBy(() -> codes.selectable(null)).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> codes.selectable("UNKNOWN")).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper, management);
    }
}
