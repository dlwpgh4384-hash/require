package com.company.common.projectboard.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProjectBoardSource {
    public String projectId, projectName, contractTypeCode, statusCode, salesPartId;
    public String pmUserId, pmName;
    public LocalDate startDate, endDate, latestMemoDate;
    public BigDecimal budgetAmount, costAmount, actualProgress;
}
