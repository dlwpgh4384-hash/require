package com.company.common.projectcostanalyses.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ProjectCostAnalysisRows {
    private ProjectCostAnalysisRows() {}
    public record Project(String projectId, String projectName, String contractTypeCode,
        String contractTypeName, String statusCode, LocalDate startDate, LocalDate endDate,
        BigDecimal originalAmount, BigDecimal contractAmount, BigDecimal budget,
        BigDecimal team, BigDecimal expense, BigDecimal plannedMm, LocalDate modifiedDate) {}
    public record History(String projectId, String itemCode, String itemName, String beforeValue,
        String afterValue, LocalDate changeDate) {}
    public record Snapshot(String yearMonth, String developerId, String projectId, String assignmentId,
        BigDecimal mm, BigDecimal freeMm, BigDecimal revenue, BigDecimal labor,
        BigDecimal team, BigDecimal expense, String contractTypeCode) {}
    public record Input(String yearMonth, String developerId, String projectId, String assignmentId,
        String jobName, String inputTypeCode, BigDecimal mm, BigDecimal revenue, BigDecimal unitPrice) {}
    public record Expense(String projectId, String yearMonth, BigDecimal amount) {}
    public record Salary(String developerId, String startMonth, String endMonth, BigDecimal amount) {}
    public record Progress(String projectId, BigDecimal actualProgress) {}
    /** 턴키 배분표(가장 나중 단계)의 사람·달 금액. 남은 예상 원가(식-20) ③에 쓴다. */
    public record PersonDistribution(String projectId, String developerId, String yearMonth, BigDecimal amount) {}
}
