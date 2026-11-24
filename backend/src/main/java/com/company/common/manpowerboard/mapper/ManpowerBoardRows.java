package com.company.common.manpowerboard.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ManpowerBoardRows {
    private ManpowerBoardRows() {}

    public record Developer(String developerId, String name, String jobName,
        LocalDate hireDate, LocalDate retirementDate) {}
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
