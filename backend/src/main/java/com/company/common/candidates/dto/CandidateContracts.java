package com.company.common.candidates.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CandidateContracts {
    private CandidateContracts() {}
    public record Items<T>(List<T> items) {}
    public record Filter(String jobCode, String gradeCode, String employeeTypeCode,
        String contactStatusCode, String availabilityWindow) {}
    public record Query(Filter filter, LocalDate availableThrough) {}
    public record Input(String name, BigDecimal desiredMonthlyRate, String employeeTypeCode,
        String companyId, String phone, String email, boolean privacyAgreement, String primaryJobCode,
        String secondaryJobCode, List<String> skills, String gradeCode, LocalDate availableDate,
        String homeAreaCode, String homeAddress, String sourceCode, String memo) {}
    public record Candidate(String candidateId, String name, BigDecimal desiredMonthlyRate,
        String employeeTypeCode, String companyId, String phone, String email, boolean privacyAgreement,
        String primaryJobCode, String secondaryJobCode, List<String> skills, String gradeCode,
        LocalDate availableDate, String homeAreaCode, String homeAddress, String sourceCode, String memo,
        String employeeTypeName, String companyName, String primaryJobName, String secondaryJobName,
        String gradeName, String sourceName, String contactStatusCode, String contactStatusName,
        LocalDate lastContactDate, Instant modifiedAt, LocalDate privacyAgreementDate,
        String privacyAgreementUserId, String privacyAgreementUserName, int daysSinceLastContact,
        boolean overdueContact) {}
    public record ContactInput(Instant modifiedAt, String contactStatusCode, String memo) {}
    public record Contact(String contactStatusCode, String contactStatusName, String memo,
        Instant recordedAt, String recordedBy, boolean systemRecord) {}
    public record Match(String opportunityId, String opportunityName, BigDecimal winningProbability,
        String jobName, String gradeName, String startMonth, BigDecimal slotMonthlyRate,
        BigDecimal expectedProfitRate, boolean lowMargin) {}
    public record Write(String candidateId, Input input, LocalDate today) {}
    public record ContactWrite(String candidateId, String status, String memo, LocalDate today, String actor) {}
}
