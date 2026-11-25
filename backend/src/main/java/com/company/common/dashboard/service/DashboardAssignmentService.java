package com.company.common.dashboard.service;

import com.company.common.assignments.dto.AssignmentContracts;
import com.company.common.assignments.service.CommonAssignmentService;
import com.company.common.manpowerboard.dto.ManpowerBoardContracts;
import com.company.common.manpowerboard.service.CommonManpowerBoardService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** MM 경고는 운영 보드, 미계약 경고는 배정 화면의 기존 판정을 사용한다. */
@Service
public class DashboardAssignmentService {
    private final CommonManpowerBoardService board;
    private final CommonAssignmentService assignments;
    private final DashboardFilters filters;

    public DashboardAssignmentService(CommonManpowerBoardService board, CommonAssignmentService assignments,
        DashboardFilters filters) {
        this.board = board;
        this.assignments = assignments;
        this.filters = filters;
    }

    /**
     * overAllocated: 운영 보드 이번 달 1.0 초과 인원, idle: 운영 보드 「지금 비어 있는 인원」(휴직·퇴사·입사 전 제외),
     * preContract: 투입 배정(UI-12) 올해·지난해 목록(중복 제외)의 「미계약 선투입」 경고 배정 수와 그 프로젝트 이름(중복 없이).
     */
    public record Counts(int overAllocated, int idle, int preContract, List<String> preContractProjects) {}

    public Counts read(LocalDate today) {
        var operating = board.getCommonManpowerBoard(filters.of(ManpowerBoardContracts.Filter.class,
            Map.of("year", today.getYear())));
        // 경고 배정 전부(2026-11-25 사용자 결정): 올해 목록만 보면 1월에 지난해 말 끝난 경고 배정이 빠지므로
        // 지난해 목록도 읽고 배정 ID로 중복을 뺀다(경고 판정은 투입 배정 UI-12 그대로).
        var byId = new java.util.LinkedHashMap<String, AssignmentContracts.Assignment>();
        for (int year : List.of(today.getYear(), today.getYear() - 1))
            assignments.getCommonAssignments(filters.of(AssignmentContracts.Filter.class,
                Map.of("year", year, "warning", "미계약 선투입"))).items()
                .forEach(a -> byId.putIfAbsent(a.assignmentId(), a));
        var pending = List.copyOf(byId.values());
        var names = pending.stream().map(a -> a.projectName()).distinct().toList();
        return new Counts(operating.summary().overAllocatedCount(), operating.summary().idleCount(),
            pending.size(), names);
    }
}
