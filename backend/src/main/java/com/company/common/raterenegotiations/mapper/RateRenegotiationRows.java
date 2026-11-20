package com.company.common.raterenegotiations.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class RateRenegotiationRows {
    private RateRenegotiationRows() {}
    public record AssignmentInfo(String assignmentId, String developerId, String developerName,
        String employeeTypeCode, String employeeTypeName, String projectId, String projectName,
        LocalDate renewalDate, BigDecimal contractMm, BigDecimal currentUnitPrice,
        String assignmentStatus, String inputTypeCode, String contractTypeCode, String projectStatus) {}
    /** 개발자 풀 월 급여(TB_HR_DEV_PRC.MON_PAY_AMT) 그 달 적용 값. */
    public record PoolSalary(String developerId, BigDecimal amount) {}
}
