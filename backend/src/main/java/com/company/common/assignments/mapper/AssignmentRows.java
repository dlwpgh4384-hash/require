package com.company.common.assignments.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class AssignmentRows {
    private AssignmentRows() {}
    public record Row(String assignmentId, String projectId, String developerId, String jobCode,
        LocalDate startDate, LocalDate endDate, BigDecimal monthlyMm, BigDecimal billingPrice,
        String inputTypeCode, String contractDeveloperId, String extensionPossibilityCode,
        int extensionCount, String earlyYn, String statusCode, Instant modifiedAt,
        String projectName, String name, String managementNumber, String jobName,
        String contractTypeCode, String projectStatusCode) {}
    public record HistoryRow(int sequence, String assignmentId, String type, LocalDate effectiveDate,
        String beforeValue, String afterValue, String reasonCode, String memo, BigDecimal price,
        BigDecimal mm, String retroactiveYn, String closedYn, String correctedYn,
        Integer target, String actor, Instant registeredAt) {}
    public record Person(String developerId, String name, String employeeTypeCode, String partId,
        String partName, LocalDate retirementDate, BigDecimal defaultPrice, boolean onLeave) {}
    public record CandidateRow(String candidateId, String projectId, String projectName, String developerId,
        String name, String jobCode, String jobName, String month, int months,
        LocalDate projectStart, LocalDate projectEnd, String statusCode, String managementNumber, Instant modifiedAt) {}
    /** 엑셀 업로드 관리번호 조회: 사용 여부·직간접 구분까지 본다. */
    public record UploadDeveloper(String id, String name, String useYn, String directClassCode) {}
    public record Write(Row row, String actor, Instant token) {}
    public record OpenRange(String id,String from) {}
    public record HistoryWrite(HistoryRow row, String actor) {}
    public record Correction(String assignmentId, int sequence, Instant token) {}
    public record MonthWrite(String assignmentId, String month, String developerId, String projectId,
        int days, BigDecimal mm, BigDecimal price, BigDecimal sales, String actor) {}
    public record ListFilter(int year, String projectId, String jobCode, String keyword) {}
    public record Amount(String assignmentId, BigDecimal value) {}
    public record Count(String assignmentId, int value) {}
}
