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
        String reason, String registeredBy, boolean ongoing, boolean retirementScheduled,
        /** 휴직 줄: 재직(복귀)으로 닫힘(시안 「재직으로 닫힘」). */ boolean closedByReturn,
        /** 재직·퇴사 줄: 그 달이 마감됨(시안 「마감됨」). */ boolean monthClosed,
        /** 재직 줄: 취소하면 돌아갈 휴직 종료일(없으면 복직일 미정). */
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate restoreEndDate) {}
    /** 근무 상태 이력 + 지금 사용자가 할 수 있는 일(시안 canStatMe·isAdminMe). */
    public record StatusList(List<Status> items, boolean canManageStatuses, boolean canCancelPastRetirement) {}
    public record UseInput(List<String> developerIds, String useYn) {}
    public record UseIssue(String developerId, String name, String reason) {}
    public record UseResult(List<String> processedDeveloperIds, List<UseIssue> rejected) {}
    /** actor = 처리자(TB_SY_CHG_HIST.REG_USER_ID). */
    public record Audit(String developerId, String type, String fieldName, String beforeValue,
        String afterValue, String reason, String actor) {}
}
