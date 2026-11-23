package com.company.common.uploads.dto;

import java.time.Instant;
import java.util.List;

public final class UploadContracts {
    private UploadContracts() {}

    public record Issue(int row, String column, String inputValue, String reason, String severity) {}
    /** B.8: 관리번호를 비운 줄에 반영 때 붙을 번호(시안 「붙을 번호」). */
    public record PlannedDeveloper(int row, String managementNumber) {}
    public record PlannedProject(int row, String projectId) {}
    /** plannedDevelopers: B.8만, yearMonth: B.1만(YYYY-MM). 다른 양식에는 생략. */
    public record Result(String uploadId, String modifiedAt, String statusName,
        int successCount, int warningCount, int errorCount, List<Issue> issues,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        List<PlannedDeveloper> plannedDevelopers,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String yearMonth,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        List<PlannedProject> plannedProjects,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Integer distributionMismatchCount) {
        public Result(String uploadId, String modifiedAt, String statusName, int successCount,
            int warningCount, int errorCount, List<Issue> issues, List<PlannedDeveloper> plannedDevelopers, String yearMonth) {
            this(uploadId, modifiedAt, statusName, successCount, warningCount, errorCount, issues, plannedDevelopers, yearMonth, null, null);
        }
    }
    /** closedMonthPolicy: B.10 마감한 달 목표가 바뀐 파일에서 고른 값(keep 마감한 달은 두기 / replace 전체 변경). 다른 양식은 생략. */
    public record Apply(Instant modifiedAt, String closedMonthPolicy) {
        public Apply(Instant modifiedAt) { this(modifiedAt, null); }
    }
    /** records: 양식별 반영할 줄(거래처 Input 등). assigned: 행번호별 붙을 번호(B.8). */
    public record Inspection(List<?> records, List<Issue> issues, int successCount,
        int warningCount, int errorCount, int totalCount, java.util.Map<Integer, String> assigned) {
        public Inspection(List<?> records, List<Issue> issues, int successCount,
            int warningCount, int errorCount, int totalCount) {
            this(records, issues, successCount, warningCount, errorCount, totalCount, java.util.Map.of());
        }
    }
    public record Check(long uploadId, String formTypeCode, String screenId, String targetMonth,
        String fileName, int fileSize, Inspection inspection) {}
    public record Completion(long uploadId, Inspection inspection, boolean applied) {}
    public record IssueWrite(long uploadId, int sequence, Issue issue) {}
}
