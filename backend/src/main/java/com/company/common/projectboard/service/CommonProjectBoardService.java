package com.company.common.projectboard.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.ProjectBoardLifecycle;
import com.company.common.common.ProjectBoardSignalRules;
import com.company.common.projectboard.dto.CommonProjectBoardContracts.*;
import com.company.common.projectboard.mapper.ProjectBoardMapper;
import com.company.common.projectboard.mapper.ProjectBoardSource;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.service.ProjectProgressRules;
import com.company.common.projects.service.ProjectStageService;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CommonProjectBoardService {
    private static final Logger log = LoggerFactory.getLogger(CommonProjectBoardService.class);
    private final ProjectBoardMapper mapper;
    private final ProjectMapper projects;
    private final BusinessCalendar calendar;
    private final CalculationRuleService definitions;
    private final ProjectBoardSignalRules signals;
    private final ProjectProgressRules progress;
    private final ProjectStageService stages;
    private final TransactionTemplate reading;

    public CommonProjectBoardService(ProjectBoardMapper mapper, ProjectMapper projects,
            BusinessCalendar calendar, CalculationRuleService definitions,
            ProjectBoardSignalRules signals, ProjectProgressRules progress, ProjectStageService stages,
            PlatformTransactionManager transactions) {
        this.mapper = mapper;
        this.projects = projects;
        this.calendar = calendar;
        this.definitions = definitions;
        this.signals = signals;
        this.progress = progress;
        this.stages = stages;
        this.reading = new TransactionTemplate(transactions);
        this.reading.setReadOnly(true);
        this.reading.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    private void validate(Filter filter) {
        if (filter.signal() != null && !List.of("위험", "주의", "정상").contains(filter.signal()))
            throw CodeFailure.invalid("signal", "신호등 조건을 확인해 주세요.");
        if (filter.contractTypeCode() != null && (filter.contractTypeCode().isBlank()
                || projects.codeName("CTRT_TYPE", filter.contractTypeCode()) == null))
            throw CodeFailure.invalid("contractTypeCode", "계약유형을 확인해 주세요.");
        if (filter.salesPartId() != null && (filter.salesPartId().isBlank()
                || !"HQ".equals(filter.salesPartId()) && !mapper.partExists(filter.salesPartId())))
            throw CodeFailure.invalid("salesPartId", "영업 파트를 확인해 주세요.");
    }

    private record Evaluated(ProjectBoardSource source, Row row, long high) {}

    /** 현황판 엑셀을 받으면 접근 기록(DNLD)을 남긴다(SFR-011-03, 이슈 엑셀과 같은 방식). */
    public void recordExcelDownload(String userId, int count) {
        mapper.insertAccess(userId, "UI-31 현황판 " + count + "건");
    }

    /**
     * 현황판 조회. 먼저 현황판에 나오는데 단계가 없는 프로젝트에 기본 단계를 만든다(프로젝트마다 따로 저장, 여러 번 불러도 한 벌).
     * 그다음 한 읽기 묶음(반복 읽기)에서 표를 계산한다. 신호등은 저장하지 않고 볼 때마다 계산한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result getCommonProjectBoard(Filter filter) {
        validate(filter);
        stages.ensureMissingStages();
        return reading.execute(status -> compute(filter));
    }

    private Result compute(Filter filter) {
        LocalDate today = calendar.today();
        String month = today.format(DateTimeFormatter.ofPattern("uuuuMM"));
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
        // 기본 정렬(시안 sortDef): 위험 → 주의 → 정상, 같으면 프로젝트명 가나다.
        Collator korean = Collator.getInstance(Locale.KOREAN);
        found.sort(Comparator.comparingInt((Evaluated value) -> rank(value.row().signal()))
            .thenComparing(value -> value.row().projectName() == null ? "" : value.row().projectName(), korean)
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
        return new Result(summary, stale, rows, calculationMethod());
    }

    /** 식 글은 TB_SY_CALC_RULE에서 CALC_ID로 읽는다. 하나를 못 읽어도 화면은 그대로 보인다(그 식만 빠짐). */
    private String calculationMethod() {
        var methods = new ArrayList<String>();
        for (String id : List.of("식-00", "식-19", "식-72", "식-73", "식-74", "식-75", "식-76", "식-91")) {
            try {
                var definition = definitions.definition(id);
                methods.add(id + ": " + definition.formula() + "\n" + definition.details());
            } catch (RuntimeException failure) {
                log.warn("계산식 {}를 읽지 못했습니다.", id, failure);
            }
        }
        methods.add("위험·주의는 1차 경보입니다. UI-04 프로젝트 원가 분석에서 상세 내역을 확인해 주세요.");
        methods.add("원가 사용률은 턴키의 저장 예산 기준입니다. 예산이 없거나 0 이하면 원가 계산 불가이며 다른 경보는 계속 판정합니다.");
        return String.join("\n\n", methods);
    }

    private Evaluated evaluate(ProjectBoardSource source, LocalDate today) {
        var steps = projects.steps(source.projectId);
        BigDecimal actual = source.actualProgress;
        if (ProjectProgressRules.measured(source.contractTypeCode) && actual == null) actual = BigDecimal.ZERO;
        var rates = progress.progress(source.contractTypeCode, steps, actual, today);
        BigDecimal planned = displayed(rates.plannedProgressRate());
        actual = displayed(rates.actualProgressRate());
        BigDecimal usage = null;
        BigDecimal costGap = null;
        boolean missingBudget = "TK".equals(source.contractTypeCode)
            && (source.budgetAmount == null || source.budgetAmount.signum() <= 0);
        if ("TK".equals(source.contractTypeCode) && !missingBudget) {
            BigDecimal cost = source.costAmount == null ? BigDecimal.ZERO : source.costAmount;
            usage = cost.multiply(BigDecimal.valueOf(100)).divide(source.budgetAmount, 1, RoundingMode.HALF_UP);
            if (actual != null) costGap = usage.subtract(actual).setScale(1, RoundingMode.HALF_UP);
        }
        var issues = mapper.issues(source.projectId);
        long[] overdue = ProjectBoardSignalRules.overdue(issues, today);
        var signal = signals.evaluate(new ProjectBoardSignalRules.Source(source.contractTypeCode,
            planned, actual, costGap, source.latestMemoDate, source.stagesCreatedDate, overdue[0], overdue[1]), today);
        // 현재 단계(시안 cur): 완료되지 않은 첫 단계, 모두 완료면 마지막 단계.
        var current = steps.stream().filter(step -> step.completedDate == null).findFirst()
            .orElse(steps.isEmpty() ? null : steps.get(steps.size() - 1));
        long open = issues.stream().filter(issue -> !"CLOSE".equals(issue.statusCode())).count();
        long high = issues.stream().filter(issue -> !"CLOSE".equals(issue.statusCode())
            && "HIGH".equals(issue.importanceCode())).count();
        var row = new Row(source.projectId, source.projectName, source.contractTypeCode,
            signal.signal(), signal.signalReasons(), signal.reasonSummary(), source.pmUserId, source.pmName,
            current == null ? null : current.stepName,
            current != null && current.completedDate == null && current.plannedEndDate != null
                && current.plannedEndDate.isBefore(today),
            planned, actual, usage, costGap, open, overdue[1], source.latestMemoDate,
            signal.daysWithoutMemo(), signal.stale(), signal.signalReasonLevels(), signal.lastUpdateDate(),
            source.salesPartId);
        return new Evaluated(source, row, high);
    }

    private static BigDecimal displayed(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }

    private static int rank(String signal) {
        return "위험".equals(signal) ? 0 : "주의".equals(signal) ? 1 : 2;
    }
}
