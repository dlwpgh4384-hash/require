package com.company.common.parts;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartScopeSourceTest {
    final PartMapper mapper = mock(PartMapper.class);
    final CommonPartService service = mock(CommonPartService.class);
    final PartScopeSource source = new PartScopeSource(service, mapper);
    PartTestData data;

    @BeforeEach void setup() {
        data = new PartTestData(mapper, mock(com.company.common.mapper.UserMapper.class));
    }

    @Test void futurePartRemainsSelectableButIsNotASettlementSourceBeforeStart() {
        data.rows.clear();
        data.period("part-a", "202612", null, "leader-a", "37.25", "1600000");
        when(service.getCommonParts()).thenReturn(new PartContracts.Items<>(List.of(new PartContracts.Part(
            "part-a", "미래 파트", "leader-a", "파트장", new java.math.BigDecimal("37.25"),
            new java.math.BigDecimal("1600000"), "2026-12", null, "Y", null, 0L, 0L, "2026-12월부터 37.25%"))));
        assertThat(source.availableForProject()).extracting(PartContracts.Part::partId).containsExactly("part-a");
        assertThat(source.settlementStandard("part-a", YearMonth.of(2026, 11))).isEmpty();
        assertThat(source.settlementStandard("part-a", YearMonth.of(2026, 12))).isPresent();
    }

    @Test void leaderSwitchesAtKoreanFirstDayWithoutRewritingPreviousPeriods() {
        data.rows.get("part-a:202601").endMonth = "202609";
        data.period("part-a", "202610", null, "leader-b", "40", "1500000");
        assertThat(source.leaderAt("part-a", Instant.parse("2026-09-30T14:59:59Z"))).contains("leader-a");
        assertThat(source.leaderAt("part-a", Instant.parse("2026-09-30T15:00:00Z"))).contains("leader-b");
        verify(mapper, never()).updatePeriod(any());
        verify(mapper, never()).insertPeriod(any());
    }
}
