package com.company.common.commonops.service;

import com.company.common.mapper.CodeHistoryRow;
import com.company.common.mapper.CommonCodeHistoryMapper;
import com.company.common.service.CommonCodeHistoryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommonCodeHistoryServiceTest {
    @Test void preservesDatabaseOrderValuesAndActualProcessorWithoutInventingHistory() {
        var mapper = mock(CommonCodeHistoryMapper.class);
        var first = new CodeHistoryRow();
        first.changedAt = Instant.parse("2026-11-13T00:00:00Z");
        first.kind = "코드";
        first.item = "CODE_NM";
        first.before = "";
        first.after = "<새 이름>";
        first.processor = "original-actor";
        var second = new CodeHistoryRow();
        second.changedAt = first.changedAt.minusSeconds(1);
        second.kind = "그룹";
        second.item = "CODE_GRP_NM";
        second.before = "옛 이름";
        second.after = "";
        second.processor = "admin";
        when(mapper.selectChanges()).thenReturn(List.of(first, second));
        var result = new CommonCodeHistoryService(mapper).getCommonCodeChangeHistory();
        assertThat(result.items()).containsExactly(first.value(), second.value());
        verify(mapper).selectChanges();
        verifyNoMoreInteractions(mapper);
    }
}
