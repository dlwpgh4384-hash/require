package com.company.common.salespipeline.mapper;

import com.company.common.salespipeline.dto.SalesPipelineContracts.Closed;
import com.company.common.salespipeline.dto.SalesPipelineContracts.ClosedFilter;
import com.company.common.salespipeline.mapper.SalesPipelineRows.*;
import com.company.common.withdrawals.mapper.WithdrawalRows.ProjectDemandRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SalesPipelineMapper {
    List<Opportunity> ongoing();
    List<Job> requiredJobs(@Param("id") String id);
    List<Job> jobs();
    List<Closed> closed(ClosedFilter filter);
    Revenue revenue(MonthQuery query);
    List<ProjectDemandRow> projectDemand(MonthQuery query);
    int candidates(@Param("jobCode") String jobCode);
    int insertAccess(@Param("userId") String userId, @Param("count") int count);
}
