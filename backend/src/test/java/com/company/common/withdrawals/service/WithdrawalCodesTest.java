package com.company.common.withdrawals.service;

import com.company.common.codegroups.dto.CodeContracts;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.mapper.CodeRows;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WithdrawalCodesTest {
    private final CodeGroupMapper mapper = mock(CodeGroupMapper.class);
    private final UserMapper management = mock(UserMapper.class);
    private final CommonCodeService source = new CommonCodeService(mapper, management);
    private final WithdrawalCodes codes = new WithdrawalCodes(source);

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

    @Test void extensionOptionsAreExactlyExistingActiveCodeLookupNotPreviewSeed() {
        var high = row("EXT_PSBL", "H", "높음 (80%)", "Y");
        var medium = row("EXT_PSBL", "M", "보통 (50%)", "Y");
        var disabled = row("EXT_PSBL", "L", "낮음 (0%)", "N");
        group("EXT_PSBL", List.of(high, medium, disabled));
        List<CodeContracts.Item> expected = source.getCommonCodes("EXT_PSBL").items().stream()
            .filter(item -> "Y".equals(item.useYn)).toList();
        assertThat(codes.options("EXT_PSBL")).usingRecursiveComparison().isEqualTo(expected);
        assertThat(codes.options("EXT_PSBL")).extracting(item -> item.code).containsExactly("H", "M");
        verifyNoInteractions(management);
    }

    @Test void unknownPossibilityIsNullInStorageEmptyInResponseAndNeverANewCode() {
        assertThat(codes.extensionStorageCode("")).isNull();
        assertThat(codes.extensionResponseCode(null)).isEmpty();
        assertThat(codes.extensionName(null)).isEqualTo("미정");
        verifyNoInteractions(mapper, management);
    }

    @Test void nullOrUnknownOrDisabledInputIsRejected() {
        group("EXT_PSBL", List.of(row("EXT_PSBL", "H", "높음", "Y"),
            row("EXT_PSBL", "L", "낮음", "N")));
        assertThatThrownBy(() -> codes.extensionStorageCode(null)).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> codes.extensionStorageCode("UNKNOWN")).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> codes.extensionStorageCode("L")).isInstanceOf(CodeFailure.class);
        assertThat(codes.extensionStorageCode("H")).isEqualTo("H");
    }

    @Test void namesAndDirectedRelatedJobsFollowChangedLookupEveryTime() {
        var backend = row("JOB", "BE", "백엔드", "Y");
        backend.addVal1 = "PL, FE";
        var frontend = row("JOB", "FE", "프론트엔드", "Y");
        group("JOB", List.of(backend, frontend));
        assertThat(codes.options("JOB")).usingRecursiveComparison()
            .isEqualTo(source.getCommonCodes("JOB").items());
        assertThat(codes.relatedJob("BE", "FE")).isTrue();
        assertThat(codes.relatedJob("FE", "BE")).isFalse();
        assertThat(codes.relatedJob("BE", "F")).isFalse();
        backend.addVal1 = "PL";
        backend.codeName = "서버 개발";
        assertThat(codes.relatedJob("BE", "FE")).isFalse();
        assertThat(codes.options("JOB").get(0).codeName).isEqualTo("서버 개발");
        verifyNoInteractions(management);
    }

    @Test void historicalDisabledNameIsPreservedForDisplay() {
        group("EXT_PSBL", List.of(row("EXT_PSBL", "L", "기존 낮음 표시명", "N")));
        assertThat(codes.extensionName("L")).isEqualTo("기존 낮음 표시명");
    }

    @Test void unrelatedGroupIsRejectedBeforeQuery() {
        assertThatThrownBy(() -> codes.options("NOT_REGISTERED")).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> codes.options(null)).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper, management);
    }
}
