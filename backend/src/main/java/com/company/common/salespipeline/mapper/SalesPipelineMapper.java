package com.company.common.salespipeline.mapper;

import com.company.common.salespipeline.dto.SalesPipelineContracts.Closed;
import com.company.common.salespipeline.dto.SalesPipelineContracts.ClosedFilter;
import com.company.common.salespipeline.mapper.SalesPipelineRows.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SalesPipelineMapper {
    List<Opportunity> ongoing();
    /** 진행 기회 전체의 필요 직무를 한 번에 읽는다(카드마다 따로 읽지 않음). */
    List<RequiredJob> requiredJobs();
    List<Job> jobs();
    List<Closed> closed(ClosedFilter filter);
    /** 종료 탭 건수(수주·실주, 사용 중지 빼고). closed 목록과 같은 조건이라 목록을 받지 않고 센다. */
    int closedCount();
    // 예측 6개월은 달·직무마다 따로 읽지 않고 아래 묶음 조회로 한 번씩 읽는다. 빈 달 목록으로는 부르지 않는다.
    List<MonthRevenue> revenues(@Param("months") List<MonthWindow> months, @Param("horizonStart") LocalDate horizonStart);
    /** 월별 예상 매출의 기회별 줄(시안 「└ 기회명 n%」). revenues의 weightedOpportunityRevenue와 같은 식·조건. */
    List<OpportunityMonthRevenue> opportunityRevenues(@Param("months") List<MonthWindow> months);
    List<MonthSupply> supply(@Param("months") List<MonthWindow> months);
    List<MonthExtension> extensions(@Param("months") List<MonthWindow> months);
    List<MonthOpportunityDemand> opportunityDemand(@Param("months") List<MonthWindow> months);
    List<MonthProjectDemand> projectDemand(@Param("months") List<MonthWindow> months, @Param("horizonStart") LocalDate horizonStart);
    List<CandidateCount> candidates();
    int insertAccess(@Param("userId") String userId, @Param("count") int count);
}
