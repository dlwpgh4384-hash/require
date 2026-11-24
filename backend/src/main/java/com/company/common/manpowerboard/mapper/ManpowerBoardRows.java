package com.company.common.manpowerboard.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ManpowerBoardRows {
    private ManpowerBoardRows() {}

    /** 직접 인력 한 명. 투입 종료일 처음·마지막은 ACT 투입 전체(읽는 기간과 상관없이)에서 본다. */
    public record Developer(String developerId, String managementNumber, String name, String jobCode,
        String jobName, String gradeCode, String employeeTypeCode, String workStatusCode,
        LocalDate hireDate, LocalDate retirementDate, LocalDate firstAssignmentEndDate,
        LocalDate lastAssignmentEndDate) {}
    /** 읽는 기간: min(연초, 이번 달 1일) ~ max(연말, 이번 달 + 3의 말일). */
    public record Period(LocalDate from, LocalDate to) {}
    public record Assignment(String assignmentId, String developerId, String projectName,
        String projectStatus, LocalDate startDate, LocalDate endDate, BigDecimal contractMm,
        String extensionCode, String extensionName) {}
    public record Monthly(String assignmentId, String month, BigDecimal monthlyMm) {}
    public record History(String assignmentId, LocalDate appliedDate, int sequence,
        BigDecimal contractMm) {}
    public record Status(String developerId, String statusCode, LocalDate startDate, LocalDate endDate) {}
    public record Tentative(String developerId, String projectName, LocalDate startDate,
        LocalDate endDate, BigDecimal winningProbability) {}
    public record Code(String groupCode, String code, String name) {}
}
