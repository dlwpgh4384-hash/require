package com.company.common.commonops.dto;

import java.math.BigDecimal;
import java.util.List;

public final class CandidateOverviewContracts {
    private CandidateOverviewContracts() {}
    public record Summary(int activeCount, int interviewScheduledCount,
        int availableByNextMonthCount, int overdueContactCount) {}
    public record Shortage(String jobCode, String jobName, String shortageStartMonth,
        BigDecimal maxShortage, int availableCandidateCount, int totalCandidateCount) {}
    public record Shortages(List<Shortage> items) {}
    public record ResumeField(String field, String value, boolean estimated, String originalText) {}
    public record ResumeExtraction(String fileName, List<ResumeField> fields, BigDecimal careerYears,
        boolean reviewRequired) {}
}
