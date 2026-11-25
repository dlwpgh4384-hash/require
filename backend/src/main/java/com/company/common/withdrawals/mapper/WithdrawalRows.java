package com.company.common.withdrawals.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class WithdrawalRows {
    private WithdrawalRows() {}
    public record Source(String assignmentId, String developerId, String name, String projectName,
        String jobCode, String jobName, LocalDate endDate, String extensionPossibilityCode,
        int extensionCount, Instant modifiedAt, String statusCode, String primaryJobCode,
        String gradeCode, String customerId, String gradeName, String projectId,
        String managementNumber) {}
    /** year가 없으면 연도로 거르지 않는다(안내 띠의 3개월 안 철수 예정자는 해를 넘겨도 센다). */
    public record ListFilter(Integer year, LocalDate from, LocalDate through,
        String extensionPossibilityCode, String jobCode) {}
    public record SharedFilter(String developerId, String opportunityId) {}
    public record Need(String opportunityId, int needSn, String opportunityName, String jobCode,
        String jobName, String gradeCode, String gradeName, String startMonth, int durationMonths,
        int requiredCount, int occupiedCount, String statusCode, String customerId,
        BigDecimal winningProbability, BigDecimal monthlyMm, String customerName, String stageName) {}
    public record Reservation(String tentativeId, String developerId, String opportunityId,
        String opportunityName, int needSn, String startMonth, String statusCode) {}
    public record ReservationWrite(String developerId, String opportunityId, int needSn,
        String startMonth, String actor, String sourceScreenId) {}
    /** 사람의 확정 투입(ACT) 한 줄: 철수 뒤 다음 투입(「다음 투입 · 프로젝트 (YYYY-MM~)」)을 찾는 데 쓴다. */
    public record FollowingRow(String developerId, String assignmentId, String projectName, LocalDate startDate) {}
    /** 조기 철수 이력 한 줄(목록 전체를 한 번에 읽는다). */
    public record EarlyRow(String assignmentId, LocalDate effectiveDate, String afterValue) {}
    /** baseMonth = 이번 달 1일(한국): 끝난 투입의 연장(식-60)과 미충원 나눗수 시작(식-61, 다음 달)을 정한다. */
    public record MonthFilter(String jobCode, String month, LocalDate firstDay, LocalDate lastDay, LocalDate baseMonth) {}
    public record SupplyRow(String developerId, BigDecimal confirmedMm) {}
    public record ExtensionRow(String developerId, BigDecimal monthlyMm, String extensionPossibilityCode) {}
    public record OpportunityDemandRow(BigDecimal requiredCount, BigDecimal monthlyMm, BigDecimal winningProbability) {}
    public record ProjectDemandRow(BigDecimal plannedMm, BigDecimal confirmedMm, int remainingMonths) {}
}
