package com.company.common.projectboard.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.ProjectBoardLifecycle;
import com.company.common.common.ProjectBoardSignalRules;
import com.company.common.projectboard.dto.CommonProjectBoardContracts.*;
import com.company.common.projectboard.mapper.ProjectBoardMapper;
import com.company.common.projectboard.mapper.ProjectBoardSource;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.service.ProjectProgressRules;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CommonProjectBoardService {
    private final ProjectBoardMapper mapper;
    private final ProjectMapper projects;
    private final BusinessCalendar calendar;
    private final CalculationRuleService definitions;
    private final ProjectBoardSignalRules signals;
    private final ProjectProgressRules progress;

    public CommonProjectBoardService(ProjectBoardMapper mapper, ProjectMapper projects,
            BusinessCalendar calendar, CalculationRuleService definitions,
            ProjectBoardSignalRules signals, ProjectProgressRules progress) {
        this.mapper = mapper;
        this.projects = projects;
        this.calendar = calendar;
        this.definitions = definitions;
        this.signals = signals;
        this.progress = progress;
    }

    private void validate(Filter filter) {
        if (filter.signal() != null && !List.of("위험", "주의", "정상").contains(filter.signal()))
            throw CodeFailure.invalid("signal", "신호등 조건을 확인해 주세요.");
        if (filter.contractTypeCode() != null && (filter.contractTypeCode().isBlank()
                || projects.codeName("CTRT_TYPE", filter.contractTypeCode()) == null))
            throw CodeFailure.invalid("contractTypeCode", "계약유형을 확인해 주세요.");
        if (filter.salesPartId() != null && (filter.salesPartId().isBlank()
                || !mapper.partExists(filter.salesPartId())))
            throw CodeFailure.invalid("salesPartId", "영업 파트를 확인해 주세요.");
    }

    private record Evaluated(ProjectBoardSource source, Row row, long high) {}

    public Result getCommonProjectBoard(Filter filter) {
        validate(filter);
        LocalDate today = calendar.today();
        String month = today.format(DateTimeFormatter.ofPattern("uuuuMM"));
        var methods = new ArrayList<String>();
        for (String id : List.of("식-00", "식-19", "식-72", "식-73", "식-74", "식-75", "식-76", "식-91")) {
            var definition = definitions.definition(id);
            methods.add(id + ": " + definition.formula() + "\n" + definition.details());
        }
        methods.add("위험·주의는 1차 경보입니다. UI-04 프로젝트 원가 분석에서 상세 내역을 확인해 주세요.");
        methods.add("원가 사용률은 턴키의 저장 예산 기준입니다. 예산이 없거나 0 이하면 원가 계산 불가이며 다른 경보는 계속 판정합니다.");
        var found = new ArrayList<Evaluated>();
        for (var source : mapper.sources(filter, today, month)) {
            if (!ProjectBoardLifecycle.visible(ProjectBoardLifecycle.effectiveStatus(
                    source.statusCode, source.endDate, today))) continue;
            // Keep Java filtering aligned even when a caller supplies another mapper adapter.
            if (filter.contractTypeCode() != null && !filter.contractTypeCode().equals(source.contractTypeCode)) continue;
            if (filter.salesPartId() != null && !filter.salesPartId().equals(source.salesPartId)) continue;
            var evaluated = evaluate(source, today);
            if (filter.signal() == null || filter.signal().equals(evaluated.row().signal())) found.add(evaluated);
        }
        found.sort(Comparator.comparingInt((Evaluated value) -> rank(value.row().signal()))
            .thenComparing(value -> value.source().startDate, Comparator.reverseOrder())
            .thenComparing(value -> value.row().projectId()));
        List<Row> rows = found.stream().map(Evaluated::row).toList();
        long danger = rows.stream().filter(row -> "위험".equals(row.signal())).count();
        long caution = rows.stream().filter(row -> "주의".equals(row.signal())).count();
        var stale = rows.stream().filter(Row::stale)
            .map(row -> new StaleProject(row.projectId(), row.projectName(), row.daysWithoutMemo())).toList();
        var summary = new Summary(new Progress(rows.size(), rows.size() - danger - caution, caution, danger),
            danger, new OpenIssues(rows.stream().mapToLong(Row::openIssueCount).sum(),
                found.stream().mapToLong(Evaluated::high).sum()),
            rows.stream().mapToLong(Row::overdueIssueCount).sum(), stale.size());
        return new Result(summary, stale, rows, String.join("\n\n", methods));
    }

    private Evaluated evaluate(ProjectBoardSource source, LocalDate today) {
        var steps = projects.steps(source.projectId);
        boolean measured = "TK".equals(source.contractTypeCode) || "IN".equals(source.contractTypeCode);
        BigDecimal actual = source.actualProgress;
        if (measured && actual == null) actual = BigDecimal.ZERO;
        var rates = progress.progress(source.contractTypeCode, steps, actual, today);
        BigDecimal planned = displayed(rates.plannedProgressRate());
        actual = displayed(rates.actualProgressRate());
        BigDecimal usage = null;
        BigDecimal costGap = null;
        boolean missingBudget = "TK".equals(source.contractTypeCode)
            && (source.budgetAmount == null || source.budgetAmount.signum() <= 0);
        if ("TK".equals(source.contractTypeCode) && !missingBudget) {
            usage = source.costAmount.multiply(BigDecimal.valueOf(100))
                .divide(source.budgetAmount, 1, RoundingMode.HALF_UP);
            if (actual != null) costGap = usage.subtract(actual).setScale(1, RoundingMode.HALF_UP);
        }
        var issues = mapper.issues(source.projectId);
        var signal = signals.evaluate(new ProjectBoardSignalRules.Source(source.contractTypeCode,
            planned, actual, costGap, source.latestMemoDate, source.startDate, issues), today);
        var current = steps.stream().filter(step -> step.completedDate == null).findFirst().orElse(null);
        long open = issues.stream().filter(issue -> !"CLOSE".equals(issue.statusCode())).count();
        long high = issues.stream().filter(issue -> !"CLOSE".equals(issue.statusCode())
            && "HIGH".equals(issue.importanceCode())).count();
        long overdue = issues.stream().filter(issue -> !"CLOSE".equals(issue.statusCode())
            && issue.dueDate() != null && issue.dueDate().isBefore(today)).count();
        String explanation = signal.reasonSummary();
        if (missingBudget) explanation += " · 저장 예산이 없거나 0 이하: 원가 계산 불가";
        if (!"정상".equals(signal.signal())) explanation += " · 1차 경보: UI-04에서 상세 확인";
        var row = new Row(source.projectId, source.projectName, source.contractTypeCode,
            signal.signal(), signal.signalReasons(), explanation, source.pmUserId, source.pmName,
            current == null ? null : current.stepName,
            current != null && current.plannedEndDate != null && current.plannedEndDate.isBefore(today),
            planned, actual, usage, costGap, open, overdue, source.latestMemoDate,
            signal.daysWithoutMemo(), signal.stale());
        return new Evaluated(source, row, high);
    }

    private static BigDecimal displayed(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }

    private static int rank(String signal) {
        return "위험".equals(signal) ? 0 : "주의".equals(signal) ? 1 : 2;
    }
}
