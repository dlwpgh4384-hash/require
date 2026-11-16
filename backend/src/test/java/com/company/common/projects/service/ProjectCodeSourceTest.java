package com.company.common.projects.service;

import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.mapper.CodeRows;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.mapper.UserMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectCodeSourceTest {
    private CodeGroupMapper mapper;
    private UserMapper management;
    private CommonCodeService service;
    private String seed;

    @BeforeEach
    void setup() throws Exception {
        mapper = mock(CodeGroupMapper.class);
        management = mock(UserMapper.class);
        service = new CommonCodeService(mapper, management);
        seed = new ClassPathResource("db/migration/V3__common_function_seed.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void absentGroupIsReportedRatherThanSeededDuringARead() {
        when(mapper.selectGroups()).thenReturn(List.of());
        assertThatThrownBy(() -> service.getCommonCodes("CTRT_TYPE"))
            .isInstanceOf(CodeFailure.class)
            .hasMessageContaining("등록된 코드 그룹이 없습니다.");
        verify(mapper).selectGroups();
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(management);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CTRT_TYPE", "EMP_TYPE", "JOB", "GRADE", "JBPS", "INPUT_TYPE",
        "DSTRB_STEP", "EXT_PSBL", "DRCT_EXPS_ITEM", "CHG_ITEM", "PROJ_STAT", "WORK_STAT",
        "ASGN_CHG_RSN", "LOSE_RSN", "CHG_TYPE", "DRCT_CLS", "SRC", "ROW_TYPE", "ASGN_STAT", "ASGN_CHG_TYPE"})
    void everyProjectGroupReadsPackagedOriginalDmlValuesWithoutWrites(String groupId) {
        var rows = codes(groupId);
        assertThat(rows).isNotEmpty();
        var group = new CodeRows.Group();
        group.groupId = groupId;
        when(mapper.selectGroups()).thenReturn(List.of(group));
        when(mapper.selectCodes(groupId)).thenReturn(rows);

        var actual = service.getCommonCodes(groupId).items();
        assertThat(actual).extracting(item -> item.code)
            .containsExactlyElementsOf(rows.stream().map(row -> row.code).toList());
        assertThat(actual).extracting(item -> item.codeName)
            .containsExactlyElementsOf(rows.stream().map(row -> row.codeName).toList());
        verify(mapper).selectGroups();
        verify(mapper).selectCodes(groupId);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(management);
    }

    @Test
    void contractAndLossOptionsAreTheOriginalCodesNotInventedEnums() {
        assertThat(codes("CTRT_TYPE")).extracting(row -> row.code)
            .containsExactly("TK", "MO", "DP", "IN");
        assertThat(codes("LOSE_RSN")).extracting(row -> row.code)
            .containsExactly("PRICE", "TECH", "SCHD", "CNCL", "ETC");
    }

    @Test
    void emptyExistingGroupDoesNotReceiveFallbackOptions() {
        var group = new CodeRows.Group();
        group.groupId = "CTRT_TYPE";
        when(mapper.selectGroups()).thenReturn(List.of(group));
        when(mapper.selectCodes("CTRT_TYPE")).thenReturn(List.of());
        assertThat(service.getCommonCodes("CTRT_TYPE").items()).isEmpty();
        verify(mapper).selectGroups();
        verify(mapper).selectCodes("CTRT_TYPE");
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(management);
    }

    private List<CodeRows.Code> codes(String groupId) {
        // The packaged migration preserves the original DML; fixtures stay inside backend/.
        var pattern = Pattern.compile("\\('" + Pattern.quote(groupId)
            + "', '([^']+)', '([^']+)', ([0-9]+), '([YN])', '([YN])',");
        var matches = pattern.matcher(seed);
        var rows = new ArrayList<CodeRows.Code>();
        while (matches.find()) {
            var row = new CodeRows.Code();
            row.groupId = groupId;
            row.code = matches.group(1);
            row.codeName = matches.group(2);
            row.sortOrd = Integer.parseInt(matches.group(3));
            row.useYn = matches.group(4);
            row.delYn = matches.group(5);
            rows.add(row);
        }
        return rows;
    }
}
