package com.company.common.airecommendations.mapper;

import com.company.common.airecommendations.dto.AiRecommendationOptionsContracts.Filter;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiRecommendationOptionsMapper {
    record TargetRow(String targetType, String targetId, String targetName, String startMonth, String endMonth) {}
    record NeedRow(String opportunityId, int needSn, String jobCode, String jobName,
        int requiredCount, String startMonth) {}

    List<TargetRow> targets(Filter filter);
    List<NeedRow> needs(@Param("opportunityIds") Collection<String> opportunityIds);
}
