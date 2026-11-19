package com.company.common.common;

import com.company.common.calculation.service.CalculationRuleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectBoardSignalRules {
    private final CalculationRuleService definitions;

    public ProjectBoardSignalRules(CalculationRuleService definitions) { this.definitions = definitions; }

    public record Issue(String issueId, String importanceCode, String statusCode, LocalDate dueDate) {}
    public record Source(String contractTypeCode, BigDecimal plannedProgress, BigDecimal actualProgress,
                         BigDecimal costProgressGap, LocalDate latestMemoDate, LocalDate contractStartDate,
                         List<Issue> issues) {}
    public record Result(String signal, List<String> signalReasons, String reasonSummary,
                         Long daysWithoutMemo, boolean stale, String explanation) {}

    public Result evaluate(Source source, LocalDate today) {
        var definition = definitions.definition("식-76");
        String formula = definition.formula().replaceAll("\\s+", "");
        if (!formula.equals("일정늦음=실적진척률−계획진척률(음수).위험:일정10%p이상늦음/진척대비원가15%p이상/기한지난“높음”이슈."
                + "주의:일정5%p이상늦음/진척대비원가5%p이상/기한지난이슈/마지막주간메모가14일넘음.그밖은정상"))
            throw new IllegalStateException("지원하지 않는 계산식입니다: 식-76");
        var reasons = new ArrayList<String>();
        boolean danger = false;
        if (source.plannedProgress() != null && source.actualProgress() != null) {
            BigDecimal lag = displayed(source.plannedProgress()).subtract(displayed(source.actualProgress()));
            if (lag.compareTo(BigDecimal.valueOf(5)) >= 0) {
                reasons.add("일정 " + lag.toPlainString() + "%p 지연");
                danger = lag.compareTo(BigDecimal.TEN) >= 0;
            }
        }
        if ("TK".equals(source.contractTypeCode()) && source.costProgressGap() != null) {
            BigDecimal gap = displayed(source.costProgressGap());
            if (gap.compareTo(BigDecimal.valueOf(5)) >= 0) {
                reasons.add("진척 대비 원가 " + gap.toPlainString() + "%p 초과");
                danger |= gap.compareTo(BigDecimal.valueOf(15)) >= 0;
            }
        }
        for (var issue : source.issues()) {
            if (!"CLOSE".equals(issue.statusCode()) && issue.dueDate() != null && issue.dueDate().isBefore(today)) {
                reasons.add("기한 지난 " + ("HIGH".equals(issue.importanceCode()) ? "높음 " : "")
                    + "이슈 " + issue.issueId());
                danger |= "HIGH".equals(issue.importanceCode());
            }
        }
        LocalDate origin = source.latestMemoDate() != null ? source.latestMemoDate() : source.contractStartDate();
        Long days = origin == null ? null : Math.max(0, ChronoUnit.DAYS.between(origin, today));
        boolean stale = days != null && days > 14;
        if (stale) reasons.add("주간 메모 " + days + "일간 업데이트 없음");
        String signal = danger ? "위험" : reasons.isEmpty() ? "정상" : "주의";
        String summary = reasons.isEmpty() ? "해당하는 경보 사유 없음" : String.join(" · ", reasons);
        return new Result(signal, List.copyOf(reasons), summary, days, stale,
            definition.formula() + "\n" + definition.details()
                + "\n위험·주의는 1차 경보입니다. UI-04 프로젝트 원가 분석의 상세 내역을 확인해 주세요.");
    }

    private static BigDecimal displayed(BigDecimal value) { return value.setScale(1, RoundingMode.HALF_UP); }
}
