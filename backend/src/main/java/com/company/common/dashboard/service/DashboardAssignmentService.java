package com.company.common.dashboard.service;

import com.company.common.assignments.dto.AssignmentContracts;
import com.company.common.assignments.service.CommonAssignmentService;
import com.company.common.manpowerboard.dto.ManpowerBoardContracts;
import com.company.common.manpowerboard.service.CommonManpowerBoardService;
import java.time.LocalDate;
import java.time.YearMonth;
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

    public record Counts(int overAllocated, int preContract) {}

    public Counts read(LocalDate today) {
        var month = YearMonth.from(today);
        var operating = board.getCommonManpowerBoard(filters.of(ManpowerBoardContracts.Filter.class,
            Map.of("year", today.getYear())));
        var pending = assignments.getCommonAssignments(filters.of(AssignmentContracts.Filter.class,
            Map.of("year", today.getYear(), "warning", "미계약 선투입")));
        long preContract = pending.items().stream().filter(a -> !a.startDate().isAfter(month.atEndOfMonth())
            && !a.endDate().isBefore(month.atDay(1))).count();
        return new Counts(operating.summary().overAllocatedCount(), Math.toIntExact(preContract));
    }
}
