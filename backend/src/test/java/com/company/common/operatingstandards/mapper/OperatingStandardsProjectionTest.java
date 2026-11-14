package com.company.common.operatingstandards.mapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class OperatingStandardsProjectionTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void actualXmlMapsEverySqlLabelWithPrecisionAndNullableDescription(boolean nullable) throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/OperatingStandardsMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        var statement = config.getMappedStatement(OperatingStandardsMapper.class.getName() + ".selectStandards");
        var mapping = statement.getResultMaps().get(0);
        var labels = new LinkedHashMap<String, Object>();
        labels.put("std_cd", "PART_SALES_EXPS"); labels.put("aply_bgn_ym", "203012");
        labels.put("std_nm", "공통관리비"); labels.put("std_val", new BigDecimal("12345678901.1234"));
        labels.put("unit_nm", "원"); labels.put("std_desc", nullable ? null : "정밀도 투영");
        labels.put("changed_by", nullable ? null : "처음 설정");
        labels.put("changed_at", nullable ? null : java.time.Instant.parse("2026-11-14T00:00:00.123456Z"));
        String sql = statement.getBoundSql(null).getSql().replaceAll("\\s+", " ").trim();
        assertThat(sql.substring(7, sql.indexOf(" FROM ")).split(",\\s*"))
            .containsExactlyElementsOf(labels.keySet());
        assertThat(mapping.getAutoMapping()).isFalse();
        assertThat(mapping.getResultMappings()).extracting(value -> value.getColumn())
            .containsExactlyElementsOf(labels.keySet());
        var row = new OperatingStandardRow();
        var meta = config.newMetaObject(row);
        for (var field : mapping.getResultMappings()) {
            assertThat(field.getJavaType()).isEqualTo(meta.getSetterType(field.getProperty()));
            assertThat(field.getTypeHandler()).isNotNull();
            meta.setValue(field.getProperty(), labels.get(field.getColumn()));
        }
        assertThat(row.standardCode).isEqualTo("PART_SALES_EXPS");
        assertThat(row.month).isEqualTo("203012");
        assertThat(row.standardName).isEqualTo("공통관리비");
        assertThat(row.value).isEqualTo(new BigDecimal("12345678901.1234"));
        assertThat(row.unit).isEqualTo("원");
        assertThat(row.description).isEqualTo(nullable ? null : "정밀도 투영");
        assertThat(row.changedBy).isEqualTo(nullable ? null : "처음 설정");
        assertThat(row.changedAt).isEqualTo(nullable ? null : java.time.Instant.parse("2026-11-14T00:00:00.123456Z"));
    }
}
