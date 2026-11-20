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
}
