package com.company.common.commonops.dto;

import java.math.BigDecimal;
import java.util.List;

public final class CandidateOverviewContracts {
    private CandidateOverviewContracts() {}
    /** totalCount: 보류·계약 완료까지 모든 후보. 나머지는 진행 중(보류·계약 완료 제외) 후보만 센다. */
    public record Summary(int totalCount, int activeCount, int interviewScheduledCount,
        int availableByNextMonthCount, int overdueContactCount) {}
    public record Shortage(String jobCode, String jobName, String shortageStartMonth,
        BigDecimal maxShortage, int availableCandidateCount, int totalCandidateCount) {}
    public record Shortages(List<Shortage> items) {}
    public record ResumeField(String field, String value, boolean estimated, String originalText) {}
    public record ResumeExtraction(String fileName, List<ResumeField> fields, BigDecimal careerYears) {}
}
