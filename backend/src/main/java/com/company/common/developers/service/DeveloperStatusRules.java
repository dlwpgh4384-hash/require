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
    private static LocalDate end(State row) { return row.endDate == null ? LocalDate.MAX : row.endDate; }
    private static String until(State row) { return row.endDate == null ? "복직일 미정" : row.endDate.toString(); }
    /** 시안 statSave: 등록 전 검사와 문구. today는 「진행 중인 휴직」을 가리는 오늘. */
    public static State append(List<State> rows, StatusInput input, String developerId, String actor, LocalDate today) {
        State row = new State(); row.developerId = developerId; row.registeredBy = actor;
        row.sequence = rows.stream().mapToInt(r -> r.sequence).max().orElse(0) + 1;
        if (row.sequence > 99999) throw fail("상태 이력 번호 범위를 초과했습니다.");
        row.code = switch(input.statusName()) { case "휴직" -> "LEAVE"; case "퇴사" -> "RETIRE"; default -> "WORK"; };
        row.startDate = input.date(); row.endDate = input.endDate(); row.reason = input.reason();
        LocalDate retire = retirement(rows);
        if (retire != null && "RETIRE".equals(row.code)) throw fail("이미 퇴사가 등록되어 있습니다.");
        if (retire != null && row.startDate.isAfter(retire)) throw fail("퇴사일(" + retire + ") 뒤의 상태는 등록할 수 없습니다.");
        if ("LEAVE".equals(row.code)) {
            if (row.endDate != null && row.endDate.isBefore(row.startDate)) throw CodeFailure.invalid("endDate", "종료일이 시작일보다 앞입니다.");
            if (rows.stream().anyMatch(r -> "LEAVE".equals(r.code) && !r.startDate.isAfter(end(row)) && !end(r).isBefore(row.startDate)))
                throw fail("이미 등록된 휴직 기간과 겹칩니다.");
        } else if ("WORK".equals(row.code)) {
            State leave = rows.stream().filter(r -> activeLeave(r, today)).findFirst()
                .orElseThrow(() -> fail("휴직 중인 사람만 재직으로 등록할 수 있습니다."));
            var already = rows.stream().filter(w -> "WORK".equals(w.code) && Integer.valueOf(leave.sequence).equals(w.closedLeaveSequence)).findFirst();
            if (already.isPresent())
                throw fail("이 휴직에는 이미 재직(복귀 " + already.get().startDate + ")이 등록되어 있습니다. 날짜를 바꾸려면 재직을 먼저 취소하세요.");
            if (!row.startDate.isAfter(leave.startDate)) throw fail("복귀일은 휴직 시작일(" + leave.startDate + ")보다 뒤여야 합니다.");
            if (leave.endDate != null && row.startDate.isAfter(leave.endDate.plusDays(1)))
                throw fail("복귀일이 휴직 종료일(" + leave.endDate + ") 다음 날보다 늦습니다. 휴직을 늘리려면 휴직 줄의 [종료일 수정]을 쓰세요. 종료일 다음 날에는 자동으로 복귀합니다.");
            if (row.reason == null || row.reason.isBlank()) row.reason = "휴직 복귀";
            row.closedLeaveSequence = leave.sequence; row.originalEndDate = leave.endDate;
            leave.endDate = row.startDate.minusDays(1);
        } else {
            var late = rows.stream().filter(w -> "WORK".equals(w.code) && !w.startDate.isBefore(row.startDate)).findFirst();
            if (late.isPresent())
                throw fail("퇴사일 뒤(같은 날 포함)에 재직(복귀 " + late.get().startDate + ")이 있습니다. 재직을 먼저 취소하거나 퇴사일을 그 뒤로 넣어 주세요.");
            State leave = rows.stream().filter(r -> "LEAVE".equals(r.code) && !end(r).isBefore(row.startDate))
                .min(Comparator.comparing(r -> r.startDate)).orElse(null);
            if (leave != null && !leave.startDate.isBefore(row.startDate))
                throw fail("퇴사일 뒤에 시작하는 휴직(" + leave.startDate + "~)이 있습니다. 그 휴직을 먼저 정리해 주세요.");
            if (leave != null) {
                row.closedLeaveSequence = leave.sequence; row.originalEndDate = leave.endDate;
                leave.endDate = row.startDate.minusDays(1);
            }
        }
        rows.add(row); validate(rows); return row;
    }
    /** 시안 statBad: 저장 전 이력 전체 검사(2026-10-27). */
    public static void validate(List<State> rows) {
        var retirements = rows.stream().filter(r -> "RETIRE".equals(r.code)).toList();
        if (retirements.size() > 1) throw fail("퇴사는 한 번만 등록할 수 있습니다.");
        LocalDate retire = retirement(rows);
        var leaves = rows.stream().filter(r -> "LEAVE".equals(r.code)).sorted(Comparator.comparing(r -> r.startDate)).toList();
        var returns = rows.stream().filter(r -> "WORK".equals(r.code)).toList();
        for (State row : rows) {
            if (row.startDate == null) throw fail("날짜를 넣어 주세요.");
            if (row.endDate != null && row.endDate.isBefore(row.startDate))
                throw fail("LEAVE".equals(row.code) ? "휴직 종료일이 시작일보다 앞입니다." : "종료일은 시작일보다 빠를 수 없습니다.");
        }
        for (State leave : leaves) {
            if (retire != null && !leave.startDate.isBefore(retire)) throw fail("퇴사일(" + retire + ") 이후에는 휴직을 넣을 수 없습니다.");
            if (retire != null && !end(leave).isBefore(retire))
                throw fail("퇴사가 " + retire + "로 잡혀 있어 휴직은 그 전날(" + retire.minusDays(1) + ")까지만 넣을 수 있습니다. 종료일을 넣어 주세요.");
            for (State other : leaves)
                if (other != leave && !leave.startDate.isAfter(end(other)) && !other.startDate.isAfter(end(leave)))
                    throw fail("휴직 기간이 다른 휴직(" + other.startDate + " ~ " + until(other) + ")과 겹칩니다.");
            for (State back : returns)
                if (!Integer.valueOf(leave.sequence).equals(back.closedLeaveSequence)
                    && !leave.startDate.isAfter(back.startDate) && !back.startDate.isAfter(end(leave)))
                    throw fail(back.startDate + " 재직(복귀)과 겹칩니다. 새 휴직은 복귀한 날 다음 날부터 넣어 주세요.");
        }
        Set<Integer> returned = new HashSet<>();
        for (State row : rows) {
            if ("WORK".equals(row.code)) {
                if (row.closedLeaveSequence == null || !returned.add(row.closedLeaveSequence))
                    throw fail("휴직당 재직 복귀는 한 번만 등록할 수 있습니다.");
                State leave = rows.stream().filter(l -> "LEAVE".equals(l.code) && l.sequence == row.closedLeaveSequence)
                    .findFirst().orElse(null);
                if (leave == null || leave.endDate == null || !leave.endDate.plusDays(1).equals(row.startDate)
                    || !row.startDate.isAfter(leave.startDate)) throw fail("재직(복귀)과 휴직 종료일이 맞지 않습니다. 재직을 먼저 취소하세요.");
                if (retire != null && !row.startDate.isBefore(retire)) throw fail("퇴사일(" + retire + ") 이후에는 재직을 넣을 수 없습니다.");
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
