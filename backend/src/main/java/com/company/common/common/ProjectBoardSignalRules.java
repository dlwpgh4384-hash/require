package com.company.common.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 식-76 신호등 판정(SFR-131-03). 프로젝트 현황판(UI-31)과 프로젝트 상세 [진행 현황] 띠가 이 한 곳을 쓴다.
 * 기준값(5·10%p, 5·15%p, 14일)은 고정값(Q-31-2)이라 코드 상수로 계산한다. 식 글은 화면이 TB_SY_CALC_RULE에서 읽어 보여 주며,
 * 글이 조금 달라도 계산을 멈추지 않는다.
 */
@Component
public class ProjectBoardSignalRules {
    public static final String DANGER = "위험";
    public static final String CAUTION = "주의";
    public static final String NORMAL = "정상";
    static final BigDecimal SCHEDULE_DANGER = BigDecimal.TEN;
    static final BigDecimal SCHEDULE_CAUTION = BigDecimal.valueOf(5);
    static final BigDecimal COST_DANGER = BigDecimal.valueOf(15);
    static final BigDecimal COST_CAUTION = BigDecimal.valueOf(5);
    static final long STALE_DAYS = 14;

    public record Issue(String issueId, String importanceCode, String statusCode, LocalDate dueDate) {}

    /**
     * 판정 재료. latestMemoDate는 가장 최근에 새로 쓴 주간 메모의 작성일(WRT_DT), baselineDate는 메모가 없을 때
     * 「업데이트 없음」을 세는 출발일(단계를 처음 만든 날, 한국 날짜. 시안 ensurePMD의 upd=오늘, 2026-11-19 사용자 결정).
     */
    public record Source(String contractTypeCode, BigDecimal plannedProgress, BigDecimal actualProgress,
                         BigDecimal costProgressGap, LocalDate latestMemoDate, LocalDate baselineDate,
                         long overdueHighIssues, long overdueIssues) {}

    /** signalReasons와 signalReasonLevels는 같은 순서(일정 → 원가 → 이슈 → 업데이트 없음). */
    public record Result(String signal, List<String> signalReasons, List<String> signalReasonLevels,
                         String reasonSummary, Long daysWithoutMemo, boolean stale, LocalDate lastUpdateDate) {}

    /** 이슈 목록에서 기한 지난 것(종료 아님·기한이 오늘 전)과 그중 높음의 수: [높음 수, 전체 수]. */
    public static long[] overdue(List<Issue> issues, LocalDate today) {
        long high = 0, all = 0;
        for (var issue : issues) {
            if ("CLOSE".equals(issue.statusCode()) || issue.dueDate() == null || !issue.dueDate().isBefore(today)) continue;
            all++;
            if ("HIGH".equals(issue.importanceCode())) high++;
        }
        return new long[] {high, all};
    }

    public Result evaluate(Source source, LocalDate today) {
        var reasons = new ArrayList<String>();
        var levels = new ArrayList<String>();
        if (source.plannedProgress() != null && source.actualProgress() != null) {
            BigDecimal lag = displayed(source.plannedProgress()).subtract(displayed(source.actualProgress()));
            if (lag.compareTo(SCHEDULE_CAUTION) >= 0)
                add(reasons, levels, lag.compareTo(SCHEDULE_DANGER) >= 0 ? DANGER : CAUTION,
                    "일정이 계획보다 " + number(lag) + "%p 늦음");
        }
        if ("TK".equals(source.contractTypeCode()) && source.costProgressGap() != null) {
            BigDecimal gap = displayed(source.costProgressGap());
            if (gap.compareTo(COST_DANGER) >= 0)
                add(reasons, levels, DANGER, "원가 사용률이 진척률보다 " + number(gap) + "%p 높음 — 원가 초과 위험");
            else if (gap.compareTo(COST_CAUTION) >= 0)
                add(reasons, levels, CAUTION, "원가 사용률이 진척률보다 " + number(gap) + "%p 높음");
        }
        if (source.overdueHighIssues() > 0)
            add(reasons, levels, DANGER, "기한 지난 중요(높음) 이슈 " + source.overdueHighIssues() + "건");
        else if (source.overdueIssues() > 0)
            add(reasons, levels, CAUTION, "기한 지난 이슈 " + source.overdueIssues() + "건");
        LocalDate origin = source.latestMemoDate() != null ? source.latestMemoDate() : source.baselineDate();
        Long days = origin == null ? null : Math.max(0, ChronoUnit.DAYS.between(origin, today));
        boolean stale = days != null && days > STALE_DAYS;
        if (stale) add(reasons, levels, CAUTION, days + "일째 상태 업데이트 없음");
        String signal = levels.contains(DANGER) ? DANGER : reasons.isEmpty() ? NORMAL : CAUTION;
        String summary = reasons.isEmpty() ? "계획대로 진행 중입니다." : String.join(" · ", reasons);
        return new Result(signal, List.copyOf(reasons), List.copyOf(levels), summary, days, stale, origin);
    }

    private static void add(List<String> reasons, List<String> levels, String level, String text) {
        reasons.add(text);
        levels.add(level);
    }

    /** 진척률은 소수 첫째 자리(Q-식-72)로 판정·표시한다. 끝의 「.0」은 떼어 「10%p」처럼 보인다. */
    public static String number(BigDecimal value) {
        BigDecimal shown = displayed(value);
        return shown.signum() == 0 ? "0" : shown.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal displayed(BigDecimal value) { return value.setScale(1, RoundingMode.HALF_UP); }
}
