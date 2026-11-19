package com.company.common.projectboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CommonProjectBoardContracts {
    private CommonProjectBoardContracts() {}

    public record Filter(String signal, String contractTypeCode, String salesPartId) {}

    /**
     * 현황판 한 줄. signalReasonLevels는 signalReasons와 같은 순서의 등급(위험·주의).
     * lastUpdateDate는 마지막 주간 메모 작성일(없으면 단계를 처음 만든 날), daysWithoutMemo는 그 뒤 지난 날 수.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Row(String projectId, String projectName, String contractTypeCode,
            String signal, List<String> signalReasons, String reasonSummary,
            String pmUserId, String pmName, String currentStepName, boolean stepDelayed,
            BigDecimal plannedProgress, BigDecimal actualProgress, BigDecimal costUsageRate,
            BigDecimal costProgressGap, long openIssueCount, long overdueIssueCount,
            LocalDate latestMemoDate, Long daysWithoutMemo, boolean stale,
            List<String> signalReasonLevels, LocalDate lastUpdateDate, String salesPartId) {}

    public record Progress(long total, long normal, long caution, long danger) {}
    public record OpenIssues(long total, long high) {}
    public record Summary(Progress progress, long riskCount, OpenIssues openIssues,
            long overdueIssueCount, long staleProjectCount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StaleProject(String projectId, String projectName, Long daysWithoutMemo) {}
    public record Result(Summary summary, List<StaleProject> staleProjects,
            List<Row> items, String calculationMethod) {}
}
