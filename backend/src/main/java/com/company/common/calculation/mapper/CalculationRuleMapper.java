package com.company.common.calculation.mapper;

import com.company.common.codegroups.mapper.CodeTextRow;
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
    /** 코드 이름에서 뒤의 괄호 설명(「높음 (80%)」의 「 (80%)」)을 뺀 표기로 찾는다. */
    List<CodeTextRow> selectLabelText(@Param("groupId") String groupId, @Param("text") String text);
}
