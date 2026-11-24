package com.company.common.manpowerboard.service;

import com.company.common.manpowerboard.dto.ManpowerBoardContracts.*;
import com.company.common.manpowerboard.mapper.ManpowerBoardRows.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ManpowerBoardRules {
    private static final DateTimeFormatter DB_MONTH = DateTimeFormatter.ofPattern("uuuuMM");
    private static final Map<String, Integer> EXTENSION_RANK = Map.of("H", 3, "M", 2, "L", 1);
    private final LocalDate today;
    private final Map<String, List<Assignment>> assignments;
    private final Map<String, BigDecimal> monthly;
    private final Map<String, List<History>> history;
    private final Map<String, List<Status>> statuses;
    private final Map<String, List<Tentative>> tentative;

    public ManpowerBoardRules(LocalDate today, List<Assignment> assignments, List<Monthly> monthly,
        List<History> history, List<Status> statuses, List<Tentative> tentative) {
        this.today = today;
        this.assignments = group(assignments, Assignment::developerId);
        this.monthly = monthly.stream().collect(Collectors.toMap(
            m -> m.assignmentId() + "|" + m.month(), Monthly::monthlyMm));
        this.history = group(history, History::assignmentId);
        this.statuses = group(statuses, Status::developerId);
        this.tentative = group(tentative, Tentative::developerId);
    }

    private static <T> Map<String, List<T>> group(List<T> rows, Function<T, String> key) {
        return rows.stream().collect(Collectors.groupingBy(key));
    }

    /** 퇴사한 다음 달부터 퇴사(시안 retOut). */
    public boolean retired(Developer developer, YearMonth month) {
        return developer.retirementDate() != null && month.isAfter(YearMonth.from(developer.retirementDate()));
    }

    /**
     * 휴직: LEAVE 기간이 그 달 1일을 덮으면 휴직. LEAVE 이력이 없으면 지금 근무 상태(WORK_STAT_CD=LEAVE)를 보고
     * 이번 달부터 휴직으로 본다(시안 p.status==='휴직').
     */
    public boolean onLeave(Developer developer, YearMonth month) {
        var leaves = statuses.getOrDefault(developer.developerId(), List.of()).stream()
            .filter(s -> "LEAVE".equals(s.statusCode())).toList();
        if (leaves.isEmpty()) return "LEAVE".equals(developer.workStatusCode()) && !month.isBefore(YearMonth.from(today));
        LocalDate first = month.atDay(1);
        return leaves.stream().anyMatch(s -> !s.startDate().isAfter(first)
            && (s.endDate() == null || !s.endDate().isBefore(first)));
    }

    public List<Assignment> personAssignments(Developer developer) {
        return assignments.getOrDefault(developer.developerId(), List.of());
    }

    public List<Assignment> active(Developer developer, YearMonth month) {
        return personAssignments(developer).stream()
            .filter(a -> overlaps(a.startDate(), a.endDate(), month)).toList();
    }

    /** 칸 판정(시안 cellInfo, SFR-128-03). 휴직·퇴사·입사 전 칸은 투입을 세지 않는다. */
    public Cell cell(Developer developer, YearMonth month) {
        var base = YearMonth.from(today);
        String key = month.toString();
        if (retired(developer, month)) return empty(key, "ret");
        if (developer.hireDate() != null && month.isBefore(YearMonth.from(developer.hireDate()))) return empty(key, "none");
        if (onLeave(developer, month)) return empty(key, "leave");
        var active = active(developer, month);
        if (!active.isEmpty()) {
            var projects = active.stream().map(a -> new Project(a.projectName(), mm(a, month))).toList();
            var total = projects.stream().map(Project::monthlyMm).reduce(BigDecimal.ZERO, BigDecimal::add);
            // 철수 예정: 이번 달 이후, 그 달에 걸친 투입이 모두 이번 달 ~ 이번 달 + 2 안에 끝난다(달로 비교).
            String code = active.stream().anyMatch(a -> "PRE".equals(a.projectStatus())) ? "pre"
                : !month.isBefore(base) && active.stream()
                    .allMatch(a -> !YearMonth.from(a.endDate()).isAfter(base.plusMonths(2))) ? "end" : "on";
            String name = switch (code) {
                case "pre" -> "미계약 선투입";
                case "end" -> "철수 예정";
                default -> active.size() >= 2 ? "동시 투입"
                    : total.compareTo(BigDecimal.ONE) < 0 && total.signum() > 0 ? "일부 투입" : "투입 중";
            };
            return new Cell(key, code, name, total, projects, "end".equals(code) ? extensionName(active) : null, null, false);
        }
        // 가배정 판정이 대기보다 먼저(시안 SFR-128-03): 진행 중 기회의 ACT 가배정이 그 달 이전부터.
        // 가배정 칸의 projects는 비운다(화면 툴팁·「N건」에 기회가 0MM 투입으로 보이지 않게). 기회명은 tentatives로 읽는다.
        var candidates = tentatives(developer, month);
        if (!candidates.isEmpty()) {
            return new Cell(key, "tent", "가배정", BigDecimal.ZERO, List.of(),
                null, candidates.get(0).winningProbability(), true);
        }
        // 지난 달은 비어 있음으로 칠하지 않는다(Q-28-4).
        if (!month.isBefore(base) && ("IDLE".equals(developer.workStatusCode())
            || developer.firstAssignmentEndDate() != null && YearMonth.from(developer.firstAssignmentEndDate()).isBefore(month)))
            return empty(key, "idle");
        return empty(key, "none");
    }

    /** 그 달 이전부터 시작한 진행 중 기회의 ACT 가배정(시작 달 순). */
    public List<Tentative> tentatives(Developer developer, YearMonth month) {
        return tentative.getOrDefault(developer.developerId(), List.of()).stream()
            .filter(t -> !YearMonth.from(t.startDate()).isAfter(month)).toList();
    }

    private static Cell empty(String month, String code) {
        String name = switch (code) {
            case "ret" -> "퇴사";
            case "leave" -> "휴직";
            case "idle" -> "비어 있음";
            default -> "–";
        };
        return new Cell(month, code, name, BigDecimal.ZERO, List.of(), null, null, false);
    }

    /** 끝나는 투입의 연장 가능성 표시명(여럿이면 가장 높은 것). 코드 이름의 「(80%)」 꼬리는 뺀다(시안 「높음」). */
    private static String extensionName(List<Assignment> active) {
        return active.stream().filter(a -> a.extensionCode() != null && a.extensionName() != null)
            .max(Comparator.comparingInt(a -> EXTENSION_RANK.getOrDefault(a.extensionCode(), 0)))
            .map(a -> a.extensionName().replaceFirst("\\s*\\(.*\\)$", "")).orElse(null);
    }

    /** 끝나는 투입 중 연장 가능성이 높음(H)인 것이 있는지. */
    public boolean highExtension(Developer developer, YearMonth month) {
        return active(developer, month).stream()
            .anyMatch(a -> !a.endDate().isBefore(today) && "H".equals(a.extensionCode()));
    }

    /** 요약 「3개월 안 철수 예정」(식-77 D-n ≥ 0, 철수 리스트와 같은 사람): 오늘 이후 끝나는 투입이 있는지. */
    public boolean assignedFromToday(Developer developer, YearMonth month) {
        return active(developer, month).stream().anyMatch(a -> !a.endDate().isBefore(today));
    }

    /** 남는 MM(식-63, 시안 freeMM): 휴직·–·퇴사는 0, 그 밖은 max(0, 1 − 그 달 MM). 지난 달은 0. */
    public BigDecimal available(Developer developer, YearMonth month) {
        if (month.isBefore(YearMonth.from(today))) return BigDecimal.ZERO;
        var cell = cell(developer, month);
        if (Set.of("leave", "none", "ret").contains(cell.statusCode())) return BigDecimal.ZERO;
        return BigDecimal.ONE.subtract(cell.totalMm()).max(BigDecimal.ZERO);
    }

    public BigDecimal mm(Assignment assignment, YearMonth month) {
        var materialized = monthly.get(assignment.assignmentId() + "|" + month.format(DB_MONTH));
        if (materialized != null) return materialized.setScale(2, RoundingMode.HALF_UP);
        var effective = history.getOrDefault(assignment.assignmentId(), List.of()).stream()
            .filter(h -> !h.appliedDate().isAfter(month.atEndOfMonth()))
            .max(Comparator.comparing((History h) -> YearMonth.from(h.appliedDate()))
                .thenComparingInt(History::sequence))
            .map(History::contractMm).orElse(assignment.contractMm());
        LocalDate start = assignment.startDate().isAfter(month.atDay(1)) ? assignment.startDate() : month.atDay(1);
        LocalDate end = assignment.endDate().isBefore(month.atEndOfMonth()) ? assignment.endDate() : month.atEndOfMonth();
        long days = Math.max(0, ChronoUnit.DAYS.between(start, end) + 1);
        return effective.multiply(BigDecimal.valueOf(days))
            .divide(BigDecimal.valueOf(month.lengthOfMonth()), 2, RoundingMode.HALF_UP);
    }

    private static boolean overlaps(LocalDate start, LocalDate end, YearMonth month) {
        return !start.isAfter(month.atEndOfMonth()) && (end == null || !end.isBefore(month.atDay(1)));
    }
}
