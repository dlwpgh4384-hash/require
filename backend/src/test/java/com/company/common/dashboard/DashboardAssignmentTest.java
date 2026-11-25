package com.company.common.dashboard;

import com.company.common.assignments.dto.AssignmentContracts;
import com.company.common.assignments.service.CommonAssignmentService;
import com.company.common.dashboard.service.DashboardAssignmentService;
import com.company.common.dashboard.service.DashboardFilters;
import com.company.common.manpowerboard.dto.ManpowerBoardContracts;
import com.company.common.manpowerboard.service.CommonManpowerBoardService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardAssignmentTest {
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    private AssignmentContracts.Assignment assignment(String id, String project, String end) {
        return json.convertValue(Map.of("assignmentId", id, "projectName", project, "endDate", end,
            "warnings", List.of("미계약 선투입")), AssignmentContracts.Assignment.class);
    }

    @Test void januaryCountsLastYearWarnedAssignmentsOnceById() {
        var board = mock(CommonManpowerBoardService.class);
        var assignments = mock(CommonAssignmentService.class);
        when(board.getCommonManpowerBoard(any())).thenReturn(new ManpowerBoardContracts.Result(2027,
            new ManpowerBoardContracts.Summary(2, null, 0, 0, 0, 1, null), List.of(), List.of(), 0));
        // 올해(2027) 목록: 해를 넘겨 이어지는 A. 지난해(2026) 목록: A(같은 배정)와 12월에 끝난 B.
        when(assignments.getCommonAssignments(argThat(f -> f != null && Integer.valueOf(2027).equals(f.year()))))
            .thenReturn(new AssignmentContracts.Items<>(List.of(assignment("A1", "선투입 A", "2027-03-31"))));
        when(assignments.getCommonAssignments(argThat(f -> f != null && Integer.valueOf(2026).equals(f.year()))))
            .thenReturn(new AssignmentContracts.Items<>(List.of(assignment("A1", "선투입 A", "2027-03-31"),
                assignment("B1", "선투입 B", "2026-12-31"))));
        var counts = new DashboardAssignmentService(board, assignments, new DashboardFilters(json))
            .read(LocalDate.of(2027, 1, 10));
        assertThat(counts.preContract()).isEqualTo(2);
        assertThat(counts.preContractProjects()).containsExactly("선투입 A", "선투입 B");
        assertThat(counts.overAllocated()).isEqualTo(1);
        assertThat(counts.idle()).isEqualTo(2);
        verify(assignments).getCommonAssignments(argThat(f -> f != null && Integer.valueOf(2027).equals(f.year())
            && "미계약 선투입".equals(f.warning())));
        verify(assignments).getCommonAssignments(argThat(f -> f != null && Integer.valueOf(2026).equals(f.year())
            && "미계약 선투입".equals(f.warning())));
    }
}
