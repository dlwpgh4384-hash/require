package com.company.common.candidates.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class CandidateRows {
    private CandidateRows() {}
    public record CandidateRow(String candidateId, String name, String employeeTypeCode,
        String companyId, String phone, String email, String privacyAgreementYn,
        LocalDate privacyAgreementDate, String privacyAgreementUserId, String primaryJobCode,
        String secondaryJobCode, String gradeCode, String homeAreaCode, String homeAddress,
        BigDecimal desiredMonthlyRate, LocalDate availableDate, String contactStatusCode,
        LocalDate lastContactDate, String sourceCode, String memo, Instant registeredAt,
        Instant modifiedAt, String companyName, String privacyAgreementUserName) {}
    public record ContactRow(String contactStatusCode, String memo, Instant recordedAt, String actor) {}
    public record Seat(String opportunityId, BigDecimal sequence, String opportunityName,
        BigDecimal winningProbability, String jobCode, String gradeCode, String startMonth,
        BigDecimal durationMonths, BigDecimal slotMonthlyRate) {}
    public record Area(String areaCode, String address) {}
}
