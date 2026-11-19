package com.company.common.common;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ProjectBoardStageCalendarTest {
    final LocalDate start = LocalDate.of(2026, 11, 1);
    List<ProjectBoardStageCalendar.Definition> definitions;

    @BeforeEach void setup() throws Exception {
        String seed = new ClassPathResource("db/migration/V3__common_function_seed.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        var matcher = Pattern.compile("\\('PROJ_STEP', '([^']+)', '([^']+)', ([0-9]+), 'Y', 'N', NULL, NULL, '([^']+)', '([^']+)', 'SYSTEM'\\)")
            .matcher(seed);
        definitions = new ArrayList<>();
        while (matcher.find()) definitions.add(new ProjectBoardStageCalendar.Definition(matcher.group(1),
            matcher.group(2), new BigDecimal(matcher.group(4)), matcher.group(5), Integer.parseInt(matcher.group(3))));
        assertThat(definitions).hasSize(12);
    }

    @ParameterizedTest @CsvSource({"TK,7", "IN,7", "MO,5", "DP,5"})
    void usesPackagedCodeMembershipWeightsAndOrder(String type, int size) {
        Collections.reverse(definitions);
        var plans = ProjectBoardStageCalendar.allocate(type, start, start.plusDays(199), definitions);
        var expected = definitions.stream().filter(row -> List.of(row.contractTypes().split(",")).contains(type))
            .sorted(java.util.Comparator.comparingInt(ProjectBoardStageCalendar.Definition::sortOrder)).toList();
        assertThat(plans).hasSize(size);
        for (int index = 0; index < size; index++) {
            assertThat(plans.get(index).code()).isEqualTo(expected.get(index).code());
            assertThat(plans.get(index).name()).isEqualTo(expected.get(index).name());
            assertThat(plans.get(index).weight()).isEqualByComparingTo(expected.get(index).weight());
            assertThat(ChronoUnit.DAYS.between(plans.get(index).plannedStartDate(), plans.get(index).plannedEndDate()) + 1)
                .isEqualTo(expected.get(index).weight().multiply(BigDecimal.valueOf(2)).longValueExact());
        }
        assertThat(plans.get(0).plannedStartDate()).isEqualTo(start);
        assertThat(plans.get(size - 1).plannedEndDate()).isEqualTo(start.plusDays(199));
    }

    @ParameterizedTest @CsvSource({"TK,7", "IN,8", "MO,5", "DP,6", "TK,10", "MO,11"})
    void reservesAtLeastOneDayPerStageWheneverPossible(String type, int duration) {
        var plans = ProjectBoardStageCalendar.allocate(type, start, start.plusDays(duration - 1), definitions);
        LocalDate next = start;
        for (var plan : plans) {
            assertThat(plan.plannedStartDate()).isEqualTo(next);
            assertThat(plan.plannedEndDate()).isAfterOrEqualTo(plan.plannedStartDate());
            next = plan.plannedEndDate().plusDays(1);
        }
        assertThat(next).isEqualTo(start.plusDays(duration));
    }

    @ParameterizedTest @CsvSource({"TK,1", "TK,2", "IN,6", "MO,1", "DP,4"})
    void shortContractsKeepEveryStageOnNondecreasingBoundaries(String type, int duration) {
        var end = start.plusDays(duration - 1);
        var plans = ProjectBoardStageCalendar.allocate(type, start, end, definitions);
        assertThat(plans).hasSize(List.of("TK", "IN").contains(type) ? 7 : 5);
        LocalDate previousStart = start;
        LocalDate previousEnd = start;
        for (var plan : plans) {
            assertThat(plan.plannedStartDate()).isAfterOrEqualTo(previousStart);
            assertThat(plan.plannedEndDate()).isAfterOrEqualTo(previousEnd).isBeforeOrEqualTo(end);
            assertThat(plan.plannedEndDate()).isAfterOrEqualTo(plan.plannedStartDate());
            previousStart = plan.plannedStartDate(); previousEnd = plan.plannedEndDate();
        }
        assertThat(plans.get(0).plannedStartDate()).isEqualTo(start);
        assertThat(previousEnd).isEqualTo(end);
    }

    @Test void cumulativeHalfDayRoundsUp() {
        var plans = ProjectBoardStageCalendar.allocate("TK", start, start.plusDays(29), definitions);
        assertThat(plans.get(0).plannedEndDate()).isEqualTo(start.plusDays(1));
        assertThat(plans.get(1).plannedStartDate()).isEqualTo(start.plusDays(2));
    }

    @Test void missingStageIsRejected() {
        definitions.remove(0);
        assertThatThrownBy(() -> ProjectBoardStageCalendar.allocate("TK", start, start.plusDays(199), definitions))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test void wrongTotalWeightIsRejected() {
        var original = definitions.get(0);
        definitions.set(0, new ProjectBoardStageCalendar.Definition(original.code(), original.name(),
            BigDecimal.ONE, original.contractTypes(), original.sortOrder()));
        assertThatThrownBy(() -> ProjectBoardStageCalendar.allocate("TK", start, start.plusDays(199), definitions))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test void duplicateCodeIsRejected() {
        var original = definitions.get(0); var second = definitions.get(1);
        definitions.set(1, new ProjectBoardStageCalendar.Definition(original.code(), second.name(),
            second.weight(), second.contractTypes(), second.sortOrder()));
        assertThatThrownBy(() -> ProjectBoardStageCalendar.allocate("TK", start, start.plusDays(199), definitions))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test void reversedPeriodIsRejected() {
        assertThatThrownBy(() -> ProjectBoardStageCalendar.allocate("TK", start, start.minusDays(1), definitions))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
