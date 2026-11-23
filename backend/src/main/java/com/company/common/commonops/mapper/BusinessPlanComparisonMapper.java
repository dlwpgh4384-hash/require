package com.company.common.commonops.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.company.common.commonops.mapper.BusinessPlanComparisonRows.Target;
import com.company.common.commonops.mapper.BusinessPlanComparisonRows.TargetWrite;

@Mapper
public interface BusinessPlanComparisonMapper {
    List<Target> targets(@Param("year") int year);
    /** B.10 반영: 목표와 마감 판단(TB_CL_CLOSE)을 같은 트랜잭션에서 고정한다. */
    void lockTargets();
    int upsertTarget(TargetWrite target);
}
