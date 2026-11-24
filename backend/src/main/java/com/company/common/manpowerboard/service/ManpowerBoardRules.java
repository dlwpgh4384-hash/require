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

    public boolean eligible(Developer developer, YearMonth month) {
        return (developer.hireDate() == null || !month.isBefore(YearMonth.from(developer.hireDate())))
            && (developer.retirementDate() == null || !month.isAfter(YearMonth.from(developer.retirementDate())))
            && statuses.getOrDefault(developer.developerId(), List.of()).stream().noneMatch(s ->
                "LEAVE".equals(s.statusCode()) && overlaps(s.startDate(), s.endDate(), month));
    }

    public boolean withdrawal(Assignment assignment) {
        return !assignment.endDate().isBefore(today)
            && !assignment.endDate().isAfter(YearMonth.from(today).plusMonths(2).atEndOfMonth());
    }

    public LocalDate withdrawalDate(Developer developer) {
        return personAssignments(developer).stream().filter(this::withdrawal)
            .map(Assignment::endDate).min(LocalDate::compareTo).orElse(null);
    }

    public List<Assignment> personAssignments(Developer developer) {
        return assignments.getOrDefault(developer.developerId(), List.of());
    }

    public List<Assignment> active(Developer developer, YearMonth month) {
        return personAssignments(developer).stream()
            .filter(a -> overlaps(a.startDate(), a.endDate(), month)).toList();
    }

    public Cell cell(Developer developer, YearMonth month) {
        var active = active(developer, month);
        var projects = active.stream().map(a -> new Project(a.projectName(), mm(a, month))).toList();
        var total = projects.stream().map(Project::monthlyMm).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean past = month.isBefore(YearMonth.from(today));
        String status;
        String extension = active.stream().filter(a -> a.extensionName() != null)
            .map(a -> a.projectName() + ": " + a.extensionName()).distinct()
            .collect(Collectors.joining(" / "));
        if (extension.isEmpty()) extension = null;
        BigDecimal probability = null;
        boolean overlay = false;
        if (developer.hireDate() != null && month.isBefore(YearMonth.from(developer.hireDate()))) {
            status = "–";
        } else if (developer.retirementDate() != null && month.isAfter(YearMonth.from(developer.retirementDate()))) {
            status = "퇴사";
        } else if (!eligible(developer, month)) {
            status = "휴직";
        } else if (active.stream().anyMatch(a -> "PRE".equals(a.projectStatus()))) {
            status = "미계약 선투입";
        } else if (!past && !active.isEmpty() && active.stream().allMatch(this::withdrawal)) {
            status = "철수 예정";
        } else if (!active.isEmpty()) {
            status = active.size() >= 2 ? "동시 투입" : total.compareTo(BigDecimal.ONE) < 0
                && total.signum() > 0 ? "일부 투입" : "투입 중";
        } else {
            var candidates = tentative.getOrDefault(developer.developerId(), List.of()).stream()
                .filter(t -> overlaps(t.startDate(), t.endDate(), month)).toList();
            if (!past && !candidates.isEmpty()) {
                status = "가배정";
                overlay = true;
                probability = candidates.get(0).winningProbability();
                projects = candidates.stream().map(t -> new Project(t.projectName(), BigDecimal.ZERO)).toList();
            } else status = past ? "–" : "비어 있음";
        }
        return new Cell(month.toString(), status, total, projects, extension, probability, overlay);
    }

    public BigDecimal available(Developer developer, YearMonth month) {
        if (!eligible(developer, month) || month.isBefore(YearMonth.from(today))) return BigDecimal.ZERO;
        return BigDecimal.ONE.subtract(cell(developer, month).totalMm()).max(BigDecimal.ZERO);
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
