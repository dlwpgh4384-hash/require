package com.company.common.projectboard.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProjectBoardSource {
    public String projectId, projectName, contractTypeCode, statusCode, salesPartId;
    public String pmUserId, pmName;
    public LocalDate startDate, endDate, latestMemoDate;
    /** 단계를 처음 만든 날(TB_PJ_STEP.REG_DTTM의 한국 날짜). 메모가 없을 때 「업데이트 없음」 출발일. */
    public LocalDate stagesCreatedDate;
    public BigDecimal budgetAmount, costAmount, actualProgress;
}
