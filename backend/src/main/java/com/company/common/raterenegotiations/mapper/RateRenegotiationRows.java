package com.company.common.raterenegotiations.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class RateRenegotiationRows {
    private RateRenegotiationRows() {}
    public record AssignmentInfo(String assignmentId, String developerId, String developerName,
        String employeeTypeCode, String employeeTypeName, String projectId, String projectName,
        LocalDate renewalDate, BigDecimal contractMm, BigDecimal currentUnitPrice,
        String assignmentStatus, String inputTypeCode, String contractTypeCode, String projectStatus) {}
}
