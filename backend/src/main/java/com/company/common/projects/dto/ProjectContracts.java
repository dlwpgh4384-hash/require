package com.company.common.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ProjectContracts {
    private ProjectContracts() {}
    public record Items<T>(List<T> items) {}
    public record ProjectList(List<Project> items, long unassignedCount) {}
    /** 예산 기본값(식-19): 예산 = 계약금액 × budgetFactor(원 단위 버림). targetMarginRate는 안내 문구용(%). */
    public record BudgetRule(java.math.BigDecimal targetMarginRate, java.math.BigDecimal budgetFactor) {}
    /** 목록 엑셀 다운로드 조건(salesPartId 「unassigned」 = 미지정). */
    public record ExcelFilter(Integer year, String contractTypeCode, String statusCode, String salesPartId, String q) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public static class Project {
        public String projectId, projectName, customerId, customerName, contractTypeCode, contractTypeName;
        public LocalDate startDate, endDate, loseDate;
        public String salesPartId, salesPartName, workplace, statusCode, statusName;
        public BigDecimal estimateAmount, originalContractAmount, contractAmount, budgetAmount;
        public String budgetManualYn;
        public BigDecimal teamExpenseAmount, projectExpenseAmount;
        public String feature, pmName, trafficLight, loseReasonName, loseMemo, distributionStepName;
        public boolean canEdit, canChangeBudget, canChangeSalesPart, canSaveManpowerPlan, canLose, canStart;
        public int preContractPeopleCount, assignedPeopleCount, distributionMismatchCount;
        public boolean distributionTotalWarning;
        public List<Assignment> assignments;
        public Progress progress;
        public List<Issue> issues;
    }
    public record Input(String projectName, String customerId, String contractTypeCode,
        LocalDate startDate, LocalDate endDate, String salesPartId, String workplace,
        String statusCode, BigDecimal estimateAmount, BigDecimal contractAmount,
        BigDecimal budgetAmount, BigDecimal teamExpenseAmount, BigDecimal projectExpenseAmount,
        String feature) {}
    public record Budget(BigDecimal budgetAmount) {}
    public record PartInput(String salesPartId, String effectiveMonth, String reason) {}
    public record ExpenseInput(String yearMonth, String itemCode, BigDecimal amount, String memo) {}
    public record LoseInput(String reasonCode, String memo) {}
    public record JobInput(String jobCode, BigDecimal plannedMm) {}
    public record ManpowerInput(List<JobInput> jobs) {}
    public record ManpowerList(List<Manpower> items, boolean canSave) {}
    public static class Manpower {
        public String jobCode, jobName;
        public BigDecimal plannedMm, actualMm, differenceMm;
    }
    public static class Expense {
        public String expenseId, yearMonth, itemCode, itemName, memo;
        public BigDecimal amount;
    }
    public record DeletedExpense(String expenseId, boolean deleted) {}
    public static class History {
        public java.time.Instant changedAt;
        public String itemName, beforeValue, afterValue, changedBy;
    }
    public static class SalesPart {
        public String salesPartId, salesPartName, effectiveMonth, reason, changedBy;
        public boolean scheduled;
    }
    public static class Assignment {
        public String name, jobName;
        public LocalDate startDate, endDate;
        public BigDecimal mm;
    }
    public record LoseImpact(long activePeopleCount, long futurePeopleCount) {}
    public static class Step {
        public String stepName;
        public BigDecimal weightRate;
        public LocalDate plannedStartDate, plannedEndDate, completedDate;
    }
    public record Progress(BigDecimal plannedProgressRate, BigDecimal actualProgressRate, List<Step> steps) {}
    public static class Issue {
        public String issueTitle, importanceName, statusName, action;
        public LocalDate dueDate;
    }
    public record Distribution(Project project, List<DistributionRow> items, List<DistributionUpload> uploads,
        List<Mismatch> mismatches, boolean totalAmountWarning) {}
    public record DistributionRow(String rowTypeCode, String managementNumber, String name,
        List<String> jobNames, String itemName, java.util.Map<String, BigDecimal> monthlyAmounts,
        BigDecimal totalAmount) {}
    public record Mismatch(String name, String kind, List<String> months) {}
    public static class DistributionUpload {
        public java.time.Instant uploadedAt;
        public String stepCode, stepName, fileName, uploadedBy;
    }
}
