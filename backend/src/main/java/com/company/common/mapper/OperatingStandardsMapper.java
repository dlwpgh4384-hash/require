package com.company.common.mapper;

import com.company.common.dto.OperatingStandardsContracts.Mutation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperatingStandardsMapper {
    List<OperatingStandardRow> selectStandards();
    String selectCurrentMonth();
    void lockStandardsAndPeriods();
    int insertStandard(Mutation value);
    int updateStandard(Mutation value);
    int deleteStandard(Mutation value);
}
