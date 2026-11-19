package com.company.common.projects.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.ProjectBoardLifecycle;
import com.company.common.common.ProjectBoardSignalRules;
import com.company.common.projects.dto.ProjectContracts.*;
import com.company.common.projects.mapper.ProjectRows.Cost;
import com.company.common.projects.mapper.ProjectRows.HealthSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectProgressRules {
    private final CalculationRuleService definitions;
    private final ProjectBoardSignalRules signals;

    public ProjectProgressRules(CalculationRuleService definitions, ProjectBoardSignalRules signals) {
        this.definitions = definitions;
        this.signals = signals;
    }

    public List<Cost> classifyCosts(List<Cost> sources, String status) {
        if ("LOSE".equals(status) && sources.stream().anyMatch(c -> "PRE".equals(c.costClassCode))) {
            String formula = definitions.definition("식-16").formula();
            if (!formula.contains("실주 시 → 대기 원가로 이동"))
                throw new IllegalStateException("지원하지 않는 계산식입니다: 식-16");
        }
        return sources.stream().map(source -> {
            Cost result = new Cost();
            result.yearMonth = source.yearMonth;
            result.costClassCode = "LOSE".equals(status) && "PRE".equals(source.costClassCode) ? "IDLE" : source.costClassCode;
            result.totalAmount = source.totalAmount;
            return result;
        }).toList();
    }

    /**
     * 프로젝트 목록·상세의 신호등(식-76): 현황판(UI-31)과 같은 판정 부품(ProjectBoardSignalRules)을 쓴다.
     * 메모·원가가 없어도 판정한다(메모가 없으면 단계를 처음 만든 날부터 센다). 종료·실주 프로젝트는 판정하지 않는다(null).
     */
    public Health signal(Project project, HealthSource source, List<Cost> costs, LocalDate today) {
        if (!ProjectBoardLifecycle.visible(ProjectBoardLifecycle.effectiveStatus(project.statusCode, project.endDate, today)))
            return null;
        HealthSource health = source == null ? new HealthSource() : source;
        Progress progress = project.progress;
        BigDecimal planned = progress == null ? null : progress.plannedProgressRate();
        BigDecimal actual = progress == null ? null : progress.actualProgressRate();
        if (actual == null && measured(project.contractTypeCode)) actual = BigDecimal.ZERO;
        BigDecimal usage = null, costGap = null;
        if ("TK".equals(project.contractTypeCode) && project.budgetAmount != null && project.budgetAmount.signum() > 0) {
            BigDecimal start = health.additionalExpenseAmount == null ? BigDecimal.ZERO : health.additionalExpenseAmount;
            BigDecimal total = (costs == null ? List.<Cost>of() : costs).stream()
                .map(c -> c.totalAmount == null ? BigDecimal.ZERO : c.totalAmount).reduce(start, BigDecimal::add);
            usage = total.multiply(BigDecimal.valueOf(100)).divide(project.budgetAmount, 1, RoundingMode.HALF_UP);
            if (actual != null) costGap = usage.subtract(actual).setScale(1, RoundingMode.HALF_UP);
        }
        var result = signals.evaluate(new ProjectBoardSignalRules.Source(project.contractTypeCode, planned, actual,
            costGap, health.latestMemoDate, health.stagesCreatedDate, health.overdueHighCount, health.overdueCount), today);
        return new Health(result, usage, costGap);
    }
    /** 신호등 판정 결과와 턴키 원가 사용률·진척 대비 원가(%, %p). */
    public record Health(ProjectBoardSignalRules.Result result, BigDecimal costUsageRate, BigDecimal costProgressGap) {}

    /** 실적 진척률을 PM이 적는 유형(턴키·내부). 안 적었으면 0%로 본다(식-74, Q-식-73). */
    public static boolean measured(String type) { return "TK".equals(type) || "IN".equals(type); }

    /** 식-73 계획 진척률. 식 글은 화면이 TB_SY_CALC_RULE에서 읽어 보여 주고, 계산은 이 코드대로 한다(글이 조금 달라도 멈추지 않음). */
    public Progress progress(String type, List<Step> steps, BigDecimal actual, LocalDate today) {
        if (steps.isEmpty()) return new Progress(null, actual, steps);
        BigDecimal planned = BigDecimal.ZERO;
        for (Step step : steps) {
            if (step.weightRate == null || step.plannedStartDate == null || step.plannedEndDate == null) return new Progress(null, actual, steps);
            if (today.isAfter(step.plannedEndDate) || today.equals(step.plannedEndDate)) planned = planned.add(step.weightRate);
            else if (!today.isBefore(step.plannedStartDate)) {
                long elapsed = ChronoUnit.DAYS.between(step.plannedStartDate, today) + 1;
                long duration = ChronoUnit.DAYS.between(step.plannedStartDate, step.plannedEndDate) + 1;
                if (duration <= 0) throw new IllegalStateException("프로젝트 단계 예정 기간을 확인해 주세요.");
                planned = planned.add(step.weightRate.multiply(BigDecimal.valueOf(elapsed)).divide(BigDecimal.valueOf(duration), 8, RoundingMode.HALF_UP));
            }
        }
        BigDecimal rate = planned.setScale(1, RoundingMode.HALF_UP);
        return new Progress(rate, List.of("MO", "DP").contains(type) ? rate : actual, steps);
    }
}
