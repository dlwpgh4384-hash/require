package com.company.common.calculation.mapper;

import com.company.common.codegroups.mapper.CodeTextRow;
import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class CalculationRuleProjectionTest {
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/CalculationRuleMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }
    private void materialize(Configuration config, String id, Object row, LinkedHashMap<String, Object> labels,
                             Map<String, Object> parameters) {
        var statement = config.getMappedStatement(CalculationRuleMapper.class.getName() + "." + id);
        var mapping = statement.getResultMaps().get(0);
        assertThat(mapping.getAutoMapping()).isFalse();
        assertThat(mapping.getType()).isEqualTo(row.getClass());
        assertThat(mapping.getResultMappings()).extracting(field -> field.getColumn())
            .containsExactlyElementsOf(labels.keySet());
        String sql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();
        assertThat(sql.substring(7, sql.indexOf(" FROM ")).replace("c.", "").split(",\\s*"))
            .containsExactlyElementsOf(labels.keySet());
        var meta = config.newMetaObject(row);
        for (var field : mapping.getResultMappings()) {
            assertThat(field.getJavaType()).isEqualTo(meta.getSetterType(field.getProperty()));
            assertThat(field.getTypeHandler()).isNotNull();
            meta.setValue(field.getProperty(), labels.get(field.getColumn()));
        }
    }
    @Test void definitionUsesExactOriginalTextLabels() throws Exception {
        var labels = new LinkedHashMap<String, Object>();
        labels.put("calc_id", "식-71"); labels.put("frml_cn", "DB 원문"); labels.put("dtl_cn", "DB 상세\n줄바꿈");
        var row = new CalculationRuleRow();
        materialize(configuration(), "selectDefinition", row, labels, Map.of("calcId", "식-71"));
        assertThat(row.value()).isEqualTo(new Definition("식-71", "DB 원문", "DB 상세\n줄바꿈"));
    }
    @Test void decimalProjectionAndPhysicalMonthRetainPrecision() throws Exception {
        var labels = new LinkedHashMap<String, Object>();
        labels.put("std_cd", "TEAM_EXPS"); labels.put("aply_bgn_ym", "202603");
        labels.put("std_val", new BigDecimal("12345678901.1234"));
        var row = new CalculationStandardRow();
        materialize(configuration(), "selectEffectiveStandard", row, labels,
            Map.of("standardCode", "TEAM_EXPS", "month", "202608"));
        assertThat(row.value()).isEqualTo(new Standard("TEAM_EXPS", YearMonth.of(2026, 3), new BigDecimal("12345678901.1234")));
    }
    @ParameterizedTest @ValueSource(strings = {"selectNormalText", "selectMappedText"})
    void textResultMapsBothIdentifiersAndDisplayName(String id) throws Exception {
        var labels = new LinkedHashMap<String, Object>();
        labels.put("code_grp_id", "EMP_TYPE"); labels.put("code", "FULL"); labels.put("code_nm", "정규직");
        var row = new CodeTextRow();
        materialize(configuration(), id, row, labels, Map.of("groupId", "EMP_TYPE", "text", "정직원"));
        assertThat(row.groupId).isEqualTo("EMP_TYPE");
        assertThat(row.code).isEqualTo("FULL");
        assertThat(row.codeName).isEqualTo("정규직");
    }
    @ParameterizedTest @ValueSource(strings = {"CASH_BASE_YM", "CASH_BASE_AMT"})
    void immediateValuesUseTheSameKoreanCurrentPhysicalRowAsPeriodSource(String code) throws Exception {
        var sql = configuration().getMappedStatement(CalculationRuleMapper.class.getName() + ".selectEffectiveStandard")
            .getBoundSql(Map.of("standardCode", code, "month", "209912"));
        assertThat(sql.getSql()).contains("clock_timestamp() AT TIME ZONE 'Asia/Seoul'", "ORDER BY aply_bgn_ym DESC LIMIT 1");
        assertThat(sql.getParameterMappings()).extracting(value -> value.getProperty()).containsExactly("standardCode");
    }
    @Test void ordinaryValuesUseRequestedMonthNotClockOrMaximumCloseMonth() throws Exception {
        var sql = configuration().getMappedStatement(CalculationRuleMapper.class.getName() + ".selectEffectiveStandard")
            .getBoundSql(Map.of("standardCode", "TEAM_EXPS", "month", "202603"));
        assertThat(sql.getSql()).doesNotContain("clock_timestamp", "MAX(", "IS NULL");
        assertThat(sql.getParameterMappings()).extracting(value -> value.getProperty()).containsExactly("standardCode", "month");
    }
}
