package com.company.common.developers.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DeveloperContracts {
    private DeveloperContracts() {}
    public record Items<T>(List<T> items) {}
    public record Filter(String employeeTypeCode, String jobCode, String workStatusName,
        String directClassCode, String q, Boolean includeStopped) {
        public Filter {
            includeStopped = Boolean.TRUE.equals(includeStopped);
        }
    }
    public record Input(String managementNumber, String name, String employeeTypeCode,
        String organizationId, String gradeCode, String primaryJobCode, String secondaryJobCode,
        String positionCode, String directClassCode, String salesPartId, LocalDate hireDate,
        BigDecimal monthlyRate, BigDecimal monthlyPay, List<String> skillCodes,
        String homeAreaCode, String homeAddress) {}
    public record Write(String developerId, Input input, String actor) {}
    public record PriceWrite(String developerId, String month, String previousMonth,
        BigDecimal monthlyRate, BigDecimal monthlyPay, String actor) {}
    public record StatusInput(String statusName, LocalDate date, LocalDate endDate, String reason) {}
    public record Status(@JsonInclude(JsonInclude.Include.NON_NULL) String statusId,
        String statusName, LocalDate startDate, LocalDate endDate,
        String reason, String registeredBy, boolean ongoing, boolean retirementScheduled) {}
    public record UseInput(List<String> developerIds, String useYn) {}
    public record UseIssue(String developerId, String name, String reason) {}
    public record UseResult(List<String> processedDeveloperIds, List<UseIssue> rejected) {}
    public record Audit(String developerId, String type, String fieldName, String beforeValue,
        String afterValue, String reason) {}
}
