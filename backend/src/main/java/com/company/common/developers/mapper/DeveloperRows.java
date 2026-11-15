package com.company.common.developers.mapper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DeveloperRows {
    private DeveloperRows() {}
    public static class Developer {
        public String developerId, managementNumber, name, employeeTypeCode, organizationId,
            gradeCode, primaryJobCode, secondaryJobCode, positionCode, directClassCode, salesPartId;
        public LocalDate hireDate, retirementDate;
        public String homeAreaCode, homeAddress, organizationName, employeeTypeName, gradeName,
            primaryJobName, workStatusName, useYn;
        public BigDecimal monthlyRate, monthlyPay;
        public List<String> skillCodes;
        /** 보유 스킬 이름(시안 상세 칩). */
        public List<String> skillNames;
        public boolean retirementScheduled;
        public String secondaryJobName, positionName, directClassName, salesPartName;
        /** 동시 수정 막기: 저장할 때 그대로 돌려보내고 다르면 409. */
        public Instant modifiedAt;
        @JsonIgnore public String workStatusCode;
    }
    public static class PayTarget {
        public String developerId, name, employeeTypeCode, directClassCode, positionCode;
        public LocalDate retirementDate;
    }
    public record MonthPay(String month, String developerId, BigDecimal pay, BigDecimal allowance,
        BigDecimal insuranceCompany, BigDecimal insuranceSelf, BigDecimal retirement, BigDecimal incomeTax,
        BigDecimal withholding, BigDecimal realPay, BigDecimal vendorSupply, String employeeTypeCode,
        String directClassCode, long uploadId, String actor) {}
    public static class ListRow {
        public String developerId, managementNumber, name, employeeTypeCode, organizationId,
            gradeCode, primaryJobCode, secondaryJobCode, positionCode, directClassCode, salesPartId;
        public LocalDate hireDate, retirementDate;
        public String organizationName, employeeTypeName, gradeName, primaryJobName, workStatusName, useYn;
        public BigDecimal monthlyRate, currentMonthMm;
        public boolean retirementScheduled, overOneMm;
        public String currentProjectName;
        public int additionalProjectCount;
        public LocalDate withdrawalDate;
    }
    public static class Assignment {
        public String projectName, jobName;
        public LocalDate startDate, endDate;
        public BigDecimal mm;
        @JsonIgnore public String assignmentId, projectId;
    }
    public static class State {
        public String developerId, code, reason, registeredBy;
        public int sequence;
        public LocalDate startDate, endDate, originalEndDate;
        public Integer closedLeaveSequence;
        public State copy() {
            var row = new State();
            row.developerId=developerId; row.code=code; row.reason=reason; row.registeredBy=registeredBy;
            row.sequence=sequence; row.startDate=startDate; row.endDate=endDate;
            row.originalEndDate=originalEndDate; row.closedLeaveSequence=closedLeaveSequence;
            return row;
        }
    }
    public static class Area {
        public String areaCode, areaName;
    }
    public static class Address {
        public String areaCode, parentAreaCode, address;
    }
    public static class Change {
        public Instant changedAt;
        public String fieldName, beforeValue, afterValue, changedBy, reason;
    }
    public static class Code {
        public String code, name;
    }
}
