package com.company.common.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CalculationRuleMapper {
    CalculationRuleRow selectDefinition(@Param("calcId") String calcId);
    CalculationStandardRow selectEffectiveStandard(@Param("standardCode") String standardCode,
                                                  @Param("month") String month);
    List<CodeTextRow> selectNormalText(@Param("groupId") String groupId, @Param("text") String text);
    CodeTextRow selectMappedText(@Param("groupId") String groupId, @Param("text") String text);
}
