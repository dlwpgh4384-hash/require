package com.company.common.common;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalCostProfitCodeSourceTest {
    private final CodeGroupMapper mapper = mock(CodeGroupMapper.class);
    private final UserMapper management = mock(UserMapper.class);
    private final CommonCodeService service = new CommonCodeService(mapper, management);
    private String seed;

    @BeforeEach
    void setup() throws Exception {
        seed = new ClassPathResource("db/migration/V3__common_function_seed.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DATA_SCOPE", "CTRT_TYPE", "EMP_TYPE", "JOB", "GRADE", "JBPS",
        "PROJ_STAT", "WORK_STAT", "LOSE_RSN", "DRCT_CLS", "COST_CLS"})
    void optionsRetainOriginalDmlCodesLabelsOrderAndFlagsWithoutBusinessWrites(String id) {
        var rows = codes(id);
        assertThat(rows).isNotEmpty();
        var group = new CodeRows.Group();
        group.groupId = id;
        when(mapper.selectGroups()).thenReturn(List.of(group));
        when(mapper.selectCodes(id)).thenReturn(rows);

        var items = service.getCommonCodes(id).items();
        assertThat(items).extracting(item -> item.code)
            .containsExactlyElementsOf(rows.stream().map(row -> row.code).toList());
        assertThat(items).extracting(item -> item.codeName)
            .containsExactlyElementsOf(rows.stream().map(row -> row.codeName).toList());
        assertThat(items).extracting(item -> item.sortOrd)
            .containsExactlyElementsOf(rows.stream().map(row -> row.sortOrd).toList());
        assertThat(items).extracting(item -> item.useYn)
            .containsExactlyElementsOf(rows.stream().map(row -> row.useYn).toList());
        verify(mapper).selectGroups();
        verify(mapper).selectCodes(id);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(management);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DATA_SCOPE", "CTRT_TYPE", "EMP_TYPE", "JOB", "GRADE", "JBPS",
        "PROJ_STAT", "WORK_STAT", "LOSE_RSN", "DRCT_CLS", "COST_CLS"})
    void emptyGroupDoesNotInventExampleOptions(String id) {
        var group = new CodeRows.Group();
        group.groupId = id;
        when(mapper.selectGroups()).thenReturn(List.of(group));
        when(mapper.selectCodes(id)).thenReturn(List.of());
        assertThat(service.getCommonCodes(id).items()).isEmpty();
        verify(mapper).selectGroups();
        verify(mapper).selectCodes(id);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(management);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DATA_SCOPE", "CTRT_TYPE", "EMP_TYPE", "JOB", "GRADE", "JBPS",
        "PROJ_STAT", "WORK_STAT", "LOSE_RSN", "DRCT_CLS", "COST_CLS"})
    void absentGroupIsReportedWithoutCreatingSeedData(String id) {
        when(mapper.selectGroups()).thenReturn(List.of());
        assertThatThrownBy(() -> service.getCommonCodes(id))
            .isInstanceOf(CodeFailure.class).hasMessageContaining("등록된 코드 그룹이 없습니다.");
        verify(mapper).selectGroups();
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(management);
    }

    private List<CodeRows.Code> codes(String id) {
        var matches = Pattern.compile("\\('" + Pattern.quote(id)
            + "', '([^']+)', '([^']+)', ([0-9]+), '([YN])', '([YN])',").matcher(seed);
        var rows = new ArrayList<CodeRows.Code>();
        while (matches.find()) {
            var row = new CodeRows.Code();
            row.groupId = id;
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
