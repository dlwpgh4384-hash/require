package com.company.common.manpowercomparisons.mapper;

import com.company.common.manpowercomparisons.dto.ManpowerComparisonContracts.Filter;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ManpowerComparisonMapper {
    List<ManpowerComparisonRows.Source> selectSources(Filter filter);

    /** SFR-011 엑셀 다운로드 접근 기록(DNLD). */
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
}
