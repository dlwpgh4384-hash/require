package com.company.common.projects.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.projects.dto.ProjectContracts.*;
import com.company.common.projects.mapper.ProjectRows.Cost;
import com.company.common.projects.mapper.ProjectRows.HealthSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ProjectProgressRules {
    private final CalculationRuleService definitions;
    public ProjectProgressRules(CalculationRuleService definitions) { this.definitions = definitions; }
    private static final Pattern TRAFFIC = Pattern.compile(
        "일정늦음=실적진척률−계획진척률\\(음수\\).위험:일정([0-9]+)%p이상늦음/진척대비원가([0-9]+)%p이상/기한지난“높음”이슈."
        + "주의:일정([0-9]+)%p이상늦음/진척대비원가([0-9]+)%p이상/기한지난이슈/마지막주간메모가([0-9]+)일넘음.그밖은정상");

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

    public String trafficLight(Project project, HealthSource source, List<Cost> costs, LocalDate today) {
        var expression = TRAFFIC.matcher(definitions.definition("식-76").formula().replaceAll("\\s+", ""));
        if (!expression.matches()) throw new IllegalStateException("지원하지 않는 계산식입니다: 식-76");
        Progress progress = project.progress;
        if (progress == null || progress.plannedProgressRate() == null || progress.actualProgressRate() == null
                || source == null || source.latestMemoDate == null) return null;
        BigDecimal lag = progress.plannedProgressRate().subtract(progress.actualProgressRate());
        BigDecimal costOver = null;
        if ("TK".equals(project.contractTypeCode)) {
            if (costs.isEmpty() || project.budgetAmount == null || project.budgetAmount.signum() <= 0) return null;
            definitions.definition("식-75");
            BigDecimal total = costs.stream().map(c -> c.totalAmount).reduce(source.additionalExpenseAmount, BigDecimal::add);
            costOver = total.multiply(BigDecimal.valueOf(100)).divide(project.budgetAmount, 1, RoundingMode.HALF_UP)
                .subtract(progress.actualProgressRate());
        }
        if (lag.compareTo(new BigDecimal(expression.group(1))) >= 0 || source.overdueHighIssue
                || costOver != null && costOver.compareTo(new BigDecimal(expression.group(2))) >= 0) return "위험";
        if (lag.compareTo(new BigDecimal(expression.group(3))) >= 0 || source.overdueIssue
                || costOver != null && costOver.compareTo(new BigDecimal(expression.group(4))) >= 0
                || ChronoUnit.DAYS.between(source.latestMemoDate, today) > Long.parseLong(expression.group(5))) return "주의";
        return "정상";
    }
    public Progress progress(String type, List<Step> steps, BigDecimal actual, LocalDate today) {
        if (steps.isEmpty()) return new Progress(null, actual, steps);
        definitions.definition("식-00"); definitions.definition("식-72");
        String expression = definitions.definition("식-73").formula().replaceAll("\\s+", "");
        if (!expression.equals("계획진척률=오늘까지끝났어야할단계비중의합+진행중인단계비중×(지난날÷그단계날수)"))
            throw new IllegalStateException("지원하지 않는 계산식입니다: 식-73");
        definitions.definition("식-74");
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
