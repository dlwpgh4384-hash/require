package com.company.common.dashboard;

import com.company.common.assignments.dto.AssignmentContracts;
import com.company.common.assignments.service.CommonAssignmentService;
import com.company.common.dashboard.mapper.DashboardMapper;
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
        var counts = new DashboardAssignmentService(board, assignments, new DashboardFilters(json), mock(DashboardMapper.class))
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

    private static ManpowerBoardContracts.Person person(String id, String novemberStatus) {
        var cells = new java.util.ArrayList<ManpowerBoardContracts.Cell>();
        for (int m = 1; m <= 12; m++) cells.add(new ManpowerBoardContracts.Cell(String.format("2026-%02d", m),
            m == 11 ? novemberStatus : "on", "", java.math.BigDecimal.ZERO, List.of(), null, null, false));
        return new ManpowerBoardContracts.Person(id, id, id, "", cells);
    }

    // 시험 회사 #46(2026-11-27): 파트장(sales1) 대기 인원은 가동률처럼 자기 파트만 센다(회사 전체 4명이 아니라 0명).
    @Test void partScopedIdleCountsOnlyOwnPartPeople() {
        var board = mock(CommonManpowerBoardService.class);
        var assignments = mock(CommonAssignmentService.class);
        var mapper = mock(DashboardMapper.class);
        when(board.getCommonManpowerBoard(any())).thenReturn(new ManpowerBoardContracts.Result(2026,
            new ManpowerBoardContracts.Summary(4, null, 0, 0, 0, 0, null),
            List.of(person("P1", "on"), person("O1", "idle"), person("O2", "idle"), person("O3", "idle"), person("O4", "idle"), person("P2", "idle")),
            List.of(), 6));
        when(assignments.getCommonAssignments(any())).thenReturn(new AssignmentContracts.Items<>(List.of()));
        when(mapper.scopedDevelopers("202611")).thenReturn(List.of("P1", "P2"));
        var service = new DashboardAssignmentService(board, assignments, new DashboardFilters(json), mapper);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            new com.company.common.support.auth.RequestAccess("sales1", java.util.Set.of(),
                new com.company.common.users.service.UserScopePolicy.Scope(false, java.util.Set.of("PART1"), java.util.Set.of()),
                true, java.util.Set.of(), java.util.Set.of(), "127.0.0.1", true, java.util.Set.of()).store(request);
            assertThat(service.read(LocalDate.of(2026, 11, 27)).idle()).isEqualTo(1);
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            // 전체 범위면 운영 보드 값 그대로.
            assertThat(service.read(LocalDate.of(2026, 11, 27)).idle()).isEqualTo(4);
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }
}
