package com.company.common.airecommendations.mapper;

import com.company.common.airecommendations.dto.AiRecommendationContracts.Condition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class AiRecommendationRows {
    private AiRecommendationRows() {}
    public record Target(String targetType, String targetId, String targetName, String startMonth,
        String endMonth, String status, String contractTypeCode, String customerId,
        String industryCode, String workAreaCode, String feature, String projectId, String customerName) {}
    public record Person(String developerId, String developerName, String managementNumber,
        String jobCode, String subJobCode, String jobName, String gradeCode, String gradeName,
        String employeeTypeCode, String employeeTypeName, String homeAreaCode) {}
    public record Evidence(int endedCount, int extendedCount, int hrWithdrawalCount,
        boolean sameCustomer, boolean sameIndustry, LocalDate withdrawalDate, boolean lowExtension) {}
    /** 여러 후보를 한 번에 읽는 근거 한 줄(사람별). */
    public record EvidenceRow(String developerId, int endedCount, int extendedCount, int hrWithdrawalCount,
        boolean sameCustomer, boolean sameIndustry, LocalDate withdrawalDate, boolean lowExtension) {
        public Evidence evidence() {
            return new Evidence(endedCount, extendedCount, hrWithdrawalCount, sameCustomer, sameIndustry, withdrawalDate, lowExtension);
        }
    }
    /** 투입이 없는 사람의 근거(한 명씩 읽을 때와 같은 0·false). */
    public static final Evidence NO_EVIDENCE = new Evidence(0, 0, 0, false, false, null, false);
    public record EvidencesFilter(java.util.Collection<String> ids, String customerId, String industryCode,
        LocalDate today, LocalDate firstDay) {}
    public record Price(String developerId, BigDecimal monthlyPrice) {}
    public record Skill(String developerId, String skillName) {}
    public record Filter(Condition condition, LocalDate firstDay, LocalDate lastDay, LocalDate today, boolean includeSameOpportunity) {}
    public record EvidenceFilter(String developerId, String customerId, String industryCode,
        LocalDate today, LocalDate firstDay) {}
    public record Log(String recommendationId, int rankOrder, String targetType, String targetId,
        String jobCode, String startMonth, String gradeCode, Integer needSn, String developerId,
        BigDecimal totalScore, String scoreDetail, String reason, String userActCode,
        String reasonCode, String actedBy, Instant actedAt) {}
    public record LogWrite(Log log, String actor) {}
    public record Action(String recommendationId, String developerId, String action,
        Integer needSn, String reasonCode, String actor) {}
    public record ExclusionWrite(Action action, String scoreDetail) {}
    public record Assignment(String assignmentId, String developerId, String projectId, String statusCode) {}
    public record Area(String code, String parentCode) {}
}
