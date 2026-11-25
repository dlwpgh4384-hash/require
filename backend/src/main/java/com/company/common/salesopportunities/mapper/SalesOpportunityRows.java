package com.company.common.salesopportunities.mapper;

import com.company.common.salesopportunities.dto.SalesOpportunityContracts.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class SalesOpportunityRows {
    private SalesOpportunityRows() {}
    public record Header(String opportunityId, String opportunityName, String customerId,
        String customerName, String salesPartId, String stageCode, BigDecimal winningProbability,
        String manualYn, BigDecimal expectedAmount, String contractTypeCode, String startMonth,
        Integer expectedMonths, String workplace, String workAreaCode, String workAreaName,
        String nextAction, LocalDate nextActionDueDate,
        String memo, String status, BigDecimal profitRate, String approvalStatus,
        BigDecimal approvedProfitRate, LocalDate resultDate, String lossReasonCode,
        String lossMemo, String projectId, String creator, Instant modifiedAt) {}
    public record NeedRow(Integer needSn, String jobCode, String gradeCode, int requiredCount,
        String startMonth, int months, BigDecimal monthlyMm, BigDecimal estimatedUnitPrice) {}
    public record Reservation(long tentativeId, String opportunityId, String opportunityName,
        int needSn, String developerId, String startMonth, String status) {}
    /** 상세 가배정 인력 줄(이름·가배정 시작월 TB_SL_TENT_ASGN.BGN_YM). */
    public record TentativeLineRow(int needSn, String developerId, String developerName, String startMonth, String status) {}
    public record Write(String id, Input input, BigDecimal profit, String approval, String manualYn, String actor) {}
    public record NeedWrite(String id, Need need, String actor) {}
    public record ApprovalWrite(String id, String status, BigDecimal profit, String reason, String actor) {}
    public record ResultWrite(String id, String status, LocalDate date, String reason, String memo, String actor) {}
    public record StageWrite(String id, String stage, BigDecimal probability, String actor) {}
    public record TentativeWrite(String id, int needSn, String developerId, String startMonth, String actor) {}
    public record Notification(String id, String recipient, String text, String actor) {}
    public record ProjectLink(String id, String projectId, LocalDate date, String actor) {}
}
