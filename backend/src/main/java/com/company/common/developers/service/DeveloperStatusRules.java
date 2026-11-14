package com.company.common.developers.service;

import com.company.common.developers.dto.DeveloperContracts.StatusInput;
import com.company.common.developers.mapper.DeveloperRows.State;
import com.company.common.support.service.CodeFailure;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DeveloperStatusRules {
    private DeveloperStatusRules() {}
    private static CodeFailure fail(String message) { return CodeFailure.invalid("date", message); }
    public static LocalDate retirement(List<State> rows) {
        return rows.stream().filter(r -> "RETIRE".equals(r.code)).map(r -> r.startDate).findFirst().orElse(null);
    }
    public static List<State> copy(List<State> rows) { return new ArrayList<>(rows.stream().map(State::copy).toList()); }
    public static State target(List<State> rows, String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,4}")) throw CodeFailure.invalid("statusId", "상태 줄을 확인해 주세요.");
        int sequence;
        try { sequence = new java.math.BigDecimal(id).intValueExact(); }
        catch (RuntimeException e) { throw CodeFailure.invalid("statusId", "상태 줄을 확인해 주세요."); }
        return rows.stream().filter(r -> r.sequence == sequence).findFirst()
            .orElseThrow(() -> CodeFailure.invalid("statusId", "상태 줄을 확인해 주세요."));
    }
    public static boolean activeLeave(State row, LocalDate day) {
        return "LEAVE".equals(row.code) && !row.startDate.isAfter(day)
            && (row.endDate == null || !row.endDate.isBefore(day));
    }
    public static State append(List<State> rows, StatusInput input, String developerId, String actor) {
        State row = new State(); row.developerId = developerId; row.registeredBy = actor;
        row.sequence = rows.stream().mapToInt(r -> r.sequence).max().orElse(0) + 1;
        if (row.sequence > 99999) throw fail("상태 이력 번호 범위를 초과했습니다.");
        row.code = switch(input.statusName()) { case "휴직" -> "LEAVE"; case "퇴사" -> "RETIRE"; default -> "WORK"; };
        row.startDate = input.date(); row.endDate = input.endDate(); row.reason = input.reason();
        LocalDate retire = retirement(rows);
        if (retire != null && ("RETIRE".equals(row.code) || row.startDate.isAfter(retire)))
            throw fail("퇴사가 이미 등록되어 있거나 퇴사일 뒤에는 상태를 등록할 수 없습니다.");
        if ("WORK".equals(row.code)) {
            State leave = rows.stream().filter(r -> "LEAVE".equals(r.code) && r.startDate.isBefore(row.startDate)
                && (r.endDate == null || !row.startDate.isAfter(r.endDate.plusDays(1)))
                && rows.stream().noneMatch(w -> "WORK".equals(w.code) && Integer.valueOf(r.sequence).equals(w.closedLeaveSequence)))
                .max(Comparator.comparing(r -> r.startDate)).orElseThrow(() -> fail("재직은 진행 중인 휴직에서 복귀할 때만 등록할 수 있습니다."));
            row.closedLeaveSequence = leave.sequence; row.originalEndDate = leave.endDate;
            leave.endDate = row.startDate.minusDays(1);
        } else if ("RETIRE".equals(row.code)) {
            State leave = rows.stream().filter(r -> activeLeave(r, row.startDate))
                .findFirst().orElse(null);
            if (leave != null) {
                row.closedLeaveSequence = leave.sequence; row.originalEndDate = leave.endDate;
                leave.endDate = row.startDate.minusDays(1);
            }
        }
        rows.add(row); validate(rows); return row;
    }
    public static void validate(List<State> rows) {
        var retirements = rows.stream().filter(r -> "RETIRE".equals(r.code)).toList();
        if (retirements.size() > 1) throw fail("퇴사는 한 번만 등록할 수 있습니다.");
        LocalDate retire = retirement(rows);
        var leaves = rows.stream().filter(r -> "LEAVE".equals(r.code)).sorted(Comparator.comparing(r -> r.startDate)).toList();
        State previous = null;
        for (State row : rows) {
            if (row.startDate == null || row.endDate != null && row.endDate.isBefore(row.startDate))
                throw fail("종료일은 시작일보다 빠를 수 없습니다.");
            if (retire != null && !"RETIRE".equals(row.code) && row.startDate.isAfter(retire))
                throw fail("퇴사일 뒤에는 상태를 등록할 수 없습니다.");
        }
        for (State leave : leaves) {
            if (retire != null && (leave.endDate == null || !leave.endDate.isBefore(retire)))
                throw fail("휴직 종료일은 퇴사 전날까지이며 반드시 입력해야 합니다.");
            if (previous != null && (previous.endDate == null || !leave.startDate.isAfter(previous.endDate)))
                throw fail("휴직 기간이 겹칩니다.");
            previous = leave;
        }
        Set<Integer> returned = new HashSet<>();
        for (State row : rows) {
            if ("WORK".equals(row.code)) {
                if (row.closedLeaveSequence == null || !returned.add(row.closedLeaveSequence))
                    throw fail("휴직당 재직 복귀는 한 번만 등록할 수 있습니다.");
                State leave = rows.stream().filter(l -> "LEAVE".equals(l.code) && l.sequence == row.closedLeaveSequence)
                    .findFirst().orElseThrow(() -> fail("연결 휴직을 확인해 주세요."));
                if (leave.endDate == null || !leave.endDate.plusDays(1).equals(row.startDate)
                    || !row.startDate.isAfter(leave.startDate)) throw fail("복귀일은 휴직 종료일 다음 날이어야 합니다.");
                if (leaves.stream().anyMatch(l -> l.startDate.equals(row.startDate)))
                    throw fail("새 휴직은 복귀 다음 날부터 등록할 수 있습니다.");
            }
            if (row.closedLeaveSequence != null && !Set.of("WORK", "RETIRE").contains(row.code))
                throw fail("휴직 연결 정보를 확인해 주세요.");
            if ("RETIRE".equals(row.code) && row.closedLeaveSequence != null) {
                State leave = rows.stream().filter(l -> "LEAVE".equals(l.code) && l.sequence == row.closedLeaveSequence)
                    .findFirst().orElseThrow(() -> fail("연결 휴직을 확인해 주세요."));
                if (!row.startDate.minusDays(1).equals(leave.endDate)) throw fail("퇴사 전날 휴직 종료를 확인해 주세요.");
            }
        }
    }
    public static void cancel(List<State> rows, State row) {
        rows.remove(row);
        if (row.closedLeaveSequence != null) {
            State leave = rows.stream().filter(l -> "LEAVE".equals(l.code) && l.sequence == row.closedLeaveSequence)
                .findFirst().orElseThrow(() -> fail("연결 휴직을 확인해 주세요."));
            LocalDate end = row.originalEndDate, retire = retirement(rows);
            if (retire != null && (end == null || !end.isBefore(retire))) {
                State retirement = rows.stream().filter(s -> "RETIRE".equals(s.code)).findFirst().orElseThrow();
                retirement.closedLeaveSequence = leave.sequence;
                retirement.originalEndDate = end;
                end = retire.minusDays(1);
            }
            leave.endDate = end;
        }
        validate(rows);
    }
}
