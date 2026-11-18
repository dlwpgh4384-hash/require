package com.company.common.manpowercomparisons.mapper;

import com.company.common.manpowercomparisons.dto.ManpowerComparisonContracts.Filter;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ManpowerComparisonMapper {
    List<ManpowerComparisonRows.Source> selectSources(Filter filter);
}
