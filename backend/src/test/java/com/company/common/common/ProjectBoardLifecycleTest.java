package com.company.common.common;

import com.company.common.support.service.CodeFailure;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

class ProjectBoardLifecycleTest {
    final LocalDate today = LocalDate.of(2026, 11, 15);

    @ParameterizedTest @CsvSource({"PRE,0,PRE", "ING,0,ING", "ING,-1,END", "PRE,-1,END", "LOSE,-1,LOSE", "LOSE,1,LOSE"})
    void effectiveStatusPreservesLostAndEndsOnlyBeforeToday(String status, int offset, String expected) {
        assertThat(ProjectBoardLifecycle.effectiveStatus(status, today.plusDays(offset), today)).isEqualTo(expected);
    }

    @ParameterizedTest @CsvSource({"PRE,true", "ING,true", "END,false", "LOSE,false"})
    void boardIncludesPreAndExcludesEndedLost(String status, boolean visible) {
        assertThat(ProjectBoardLifecycle.visible(status)).isEqualTo(visible);
    }

    @ParameterizedTest @CsvSource({"PRE,0,true", "ING,0,true", "ING,1,false", "END,0,false", "LOSE,0,false"})
    void ensureIsNoOpForExistingOrEndedLost(String status, int count, boolean expected) {
        assertThat(ProjectBoardLifecycle.shouldEnsure(status, count)).isEqualTo(expected);
    }

    @ParameterizedTest @EnumSource(value = ProjectBoardLifecycle.Command.class, names = "UPDATE_ISSUE", mode = EnumSource.Mode.EXCLUDE)
    void endedCommandsReturnFieldValidationError(ProjectBoardLifecycle.Command command) {
        assertThatThrownBy(() -> ProjectBoardLifecycle.requireWritable("END", command))
            .isInstanceOfSatisfying(CodeFailure.class, failure -> {
                assertThat(failure.getStatus()).isEqualTo(400);
                assertThat(failure.getField()).isEqualTo("projectId");
            });
    }

    @ParameterizedTest @EnumSource(value = ProjectBoardLifecycle.Command.class, names = "UPDATE_ISSUE", mode = EnumSource.Mode.EXCLUDE)
    void lostCommandsReturnFieldValidationError(ProjectBoardLifecycle.Command command) {
        assertThatThrownBy(() -> ProjectBoardLifecycle.requireWritable("LOSE", command))
            .isInstanceOfSatisfying(CodeFailure.class, failure -> assertThat(failure.getStatus()).isEqualTo(400));
    }

    @Test void existingIssuesRemainEditableForEndedAndLostProjects() {
        assertThatCode(() -> ProjectBoardLifecycle.requireWritable("END", ProjectBoardLifecycle.Command.UPDATE_ISSUE))
            .doesNotThrowAnyException();
        assertThatCode(() -> ProjectBoardLifecycle.requireWritable("LOSE", ProjectBoardLifecycle.Command.UPDATE_ISSUE))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest @EnumSource(ProjectBoardLifecycle.Command.class)
    void activeCommandsAreNotRestrictedByYearOrLogin(ProjectBoardLifecycle.Command command) {
        assertThatCode(() -> ProjectBoardLifecycle.requireWritable("ING", command)).doesNotThrowAnyException();
        assertThatCode(() -> ProjectBoardLifecycle.requireWritable("PRE", command)).doesNotThrowAnyException();
    }
}
