package com.company.common;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** --run-daily-batch[=YYYY-MM-DD] 명령 선택지(HTTP API 없음, 한 번 돌고 끝남). */
class DailyBatchCommandTest {
    @Test void optionWithoutDateMeansTodayAndWithDateMeansThatDay() {
        assertThat(NavigationApplication.dailyBatchRequested(new String[]{"--run-daily-batch"})).isTrue();
        assertThat(NavigationApplication.dailyBatchDay(new String[]{"--run-daily-batch"})).isEqualTo(Optional.empty());
        assertThat(NavigationApplication.dailyBatchDay(new String[]{"--x", "--run-daily-batch=2026-11-27"}))
            .contains(LocalDate.of(2026, 11, 27));
        assertThat(NavigationApplication.dailyBatchRequested(new String[]{"--prepare-schema"})).isFalse();
        assertThat(NavigationApplication.dailyBatchRequested(new String[]{"--run-daily-batches"})).isFalse();
    }

    @Test void malformedDateIsRefused() {
        for (String bad : new String[]{"--run-daily-batch=2026-13-01", "--run-daily-batch=20261127", "--run-daily-batch="})
            assertThatThrownBy(() -> NavigationApplication.dailyBatchDay(new String[]{bad}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
