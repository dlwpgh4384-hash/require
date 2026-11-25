package com.company.common.airecommendations.mapper;

import com.company.common.airecommendations.dto.AiRecommendationContracts.Condition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class AiRecommendationRows {
    private AiRecommendationRows() {}
    public record Target(String targetType, String targetId, String targetName, String startMonth,
        String endMonth, String status, String contractTypeCode, String customerId,
        String industryCode, String workAreaCode, String feature, String projectId) {}
    public record Person(String developerId, String developerName, String managementNumber,
        String jobCode, String subJobCode, String jobName, String gradeCode, String gradeName,
        String employeeTypeCode, String employeeTypeName, String homeAreaCode) {}
    public record Evidence(int endedCount, int extendedCount, int hrWithdrawalCount,
        boolean sameCustomer, boolean sameIndustry, LocalDate withdrawalDate, boolean lowExtension) {}
    public record Price(BigDecimal monthlyPrice) {}
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
    public record Assignment(String assignmentId, String developerId, String projectId) {}
    public record Area(String code, String parentCode) {}
}
