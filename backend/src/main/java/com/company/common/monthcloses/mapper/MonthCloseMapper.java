package com.company.common.monthcloses.mapper;

import com.company.common.assignments.mapper.AssignmentRows.MonthWrite;
import com.company.common.monthcloses.dto.MonthCloseContracts.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Cost;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Snapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MonthCloseMapper {
    void lockSources();
    Header selectHeader(@Param("month") String month);
    String latestClosedMonth();
    Counts selectCounts(@Param("month") String month);
    int adjustmentCount(@Param("month") String month, @Param("next") String next);
    int saveClose(Mutation mutation);
    int insertHistory(History history);
    int insertAccess(@Param("month") String month, @Param("actor") String actor, @Param("action") String action);
    int insertWorkMonth(@Param("month") String month, @Param("actor") String actor);
    int saveChecks(@Param("month") String month, @Param("checks") java.util.List<StoredCheck> checks,
        @Param("actor") String actor);
    int fixedAssignmentCount(@Param("month") String month);
    int fixedCostCount(@Param("month") String month);
    int saveAssignment(MonthWrite row);
    int deleteOpenAssignments(@Param("month") String month);
    int deleteOpenCosts(@Param("month") String month);
    int saveCost(@Param("row") Cost row, @Param("classification") String classification,
        @Param("partId") String partId, @Param("actor") String actor);
    int saveProfit(@Param("row") Snapshot row, @Param("actor") String actor);
    int unfixAssignments(@Param("month") String month);
    int unfixCosts(@Param("month") String month);
    int unfixProfit(@Param("month") String month);
}
