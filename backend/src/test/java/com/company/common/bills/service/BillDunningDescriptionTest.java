package com.company.common.bills.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 미수 알림 받는 사람 설명은 300자 안에서 「외 n명」으로 줄인다(2026-11-24 사용자 결정). */
class BillDunningDescriptionTest {
    @Test void shortListStaysWhole() {
        assertThat(BillDunningService.fitDescription(List.of("김영업 (a)", "박파트장 (b)"), "")).isEqualTo("김영업 (a), 박파트장 (b)");
    }
    @Test void longListIsCutWithOthersCountAndKeepsTail() {
        var people = new ArrayList<String>();
        for (int i = 0; i < 40; i++) people.add("경영진" + i + " (user" + i + ")");
        String text = BillDunningService.fitDescription(people, " (PM 없음, 대신 보냄)");
        assertThat(text.codePointCount(0, text.length())).isLessThanOrEqualTo(300);
        assertThat(text).startsWith("경영진0 (user0), ").endsWith("명 (PM 없음, 대신 보냄)");
    }
    @Test void oneVeryLongNameIsShortenedButStillFits() {
        String text = BillDunningService.fitDescription(List.of("가".repeat(400), "나 (b)"), "");
        assertThat(text.codePointCount(0, text.length())).isLessThanOrEqualTo(300);
        assertThat(text).endsWith("… 외 1명");
    }
}
