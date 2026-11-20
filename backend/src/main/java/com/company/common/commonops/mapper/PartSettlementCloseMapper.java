package com.company.common.commonops.mapper;

import com.company.common.commonops.dto.PartSettlementCloseContracts.Period;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PartSettlementCloseMapper {
    List<PartSettlementCloseRow> selectTargets(Period period);
}
