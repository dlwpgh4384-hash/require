package com.company.common.projects.mapper;

import com.company.common.projects.dto.ProjectContracts.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class ProjectRows {
    private ProjectRows() {}
    public record Write(Project project, String actor) {}
    public record Audit(String projectId, String code, String name, String beforeValue,
        String afterValue, String actor) {}
    public record PartWrite(String projectId, String month, String partId, String reason, String actor) {}
    public record ExpenseKey(String projectId, String month, int sequence) {}
    public record ExpenseWrite(ExpenseKey key, ExpenseInput input, String actor) {}
    public record PlanWrite(String projectId, JobInput job, String actor) {}
    public record LossWrite(String projectId, LoseInput input, LocalDate today, String actor) {}
    public record AssignmentWrite(String assignmentId, String kind, LocalDate today, String memo, String actor) {}
    public static class ActiveAssignment {
        public String assignmentId, developerId;
        public LocalDate startDate, endDate;
    }
    public static class Outside {
        public long assignments, distributionMonths, expenses, started;
    }
    public static class Cell {
        public String rowTypeCode, managementNumber, developerId, name, itemName, yearMonth;
        public int rowNumber;
        public BigDecimal amount;
    }
    public static class Job {
        public String code, name;
    }
    public static class HealthSource {
        public LocalDate latestMemoDate;
        public boolean overdueHighIssue, overdueIssue;
        public BigDecimal additionalExpenseAmount = BigDecimal.ZERO;
    }
    public static class Cost {
        public String yearMonth, costClassCode;
        public BigDecimal totalAmount;
    }
    public static class MismatchCell {
        public String name, kind, yearMonth;
    }
}
