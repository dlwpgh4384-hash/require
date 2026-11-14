package com.company.common.mapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class CodeGroupProjectionTest {
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/CodeGroupMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();
        }
        return config;
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void realXmlMapsEveryPhysicalFieldIncludingNullableDecimals(boolean nullable) throws Exception {
        var config=configuration();
        var statement=config.getMappedStatement(CodeGroupMapper.class.getName()+".selectCodes");
        var mapping=statement.getResultMaps().get(0);
        var labels=new LinkedHashMap<String,Object>();
        labels.put("code_grp_id","JOB"); labels.put("code","TEST"); labels.put("code_nm","이름");
        labels.put("sort_ord",87654); labels.put("use_yn","N"); labels.put("del_yn","Y");
        labels.put("code_val",nullable?null:new BigDecimal("12345678901.1234"));
        labels.put("unit_nm",nullable?null:"원"); labels.put("add_val1",nullable?null:"ONE,TWO");
        labels.put("add_val2",nullable?null:"1234.1250"); labels.put("add_val3",nullable?null:"보존");
        labels.put("changed_at",nullable?null:java.time.Instant.parse("2026-11-14T00:00:00.123456Z"));
        String sql=statement.getBoundSql("JOB").getSql().replaceAll("\\s+"," ").trim();
        // 마지막 변경 시각은 COALESCE(mod_dttm, reg_dttm) AS changed_at으로 읽는다(동시 수정 검사).
        assertThat(sql.substring(7,sql.indexOf(" FROM ")).replace("COALESCE(mod_dttm, reg_dttm) AS changed_at","changed_at").split(",\\s*"))
            .containsExactlyElementsOf(labels.keySet());
        assertThat(mapping.getAutoMapping()).isFalse();
        assertThat(mapping.getType()).isEqualTo(CodeRows.Code.class);
        assertThat(mapping.getResultMappings()).extracting(field->field.getColumn()).containsExactlyElementsOf(labels.keySet());
        var row=new CodeRows.Code(); var meta=config.newMetaObject(row);
        for(var field:mapping.getResultMappings()) {
            assertThat(field.getJavaType()).isEqualTo(meta.getSetterType(field.getProperty()));
            assertThat(field.getTypeHandler()).isNotNull();
            meta.setValue(field.getProperty(),labels.get(field.getColumn()));
        }
        assertThat(row.code).isEqualTo("TEST"); assertThat(row.sortOrd).isEqualTo(87654);
        assertThat(row.delYn).isEqualTo("Y"); assertThat(row.useYn).isEqualTo("N");
        assertThat(row.codeVal).isEqualTo(labels.get("code_val"));
        assertThat(row.addVal3).isEqualTo(labels.get("add_val3"));
        assertThat(row.value().relatedJobs).isEqualTo(labels.get("add_val1"));
        assertThat(row.value().estimateUnitPrice).isEqualTo(nullable?null:new BigDecimal("1234.1250"));
        assertThat(row.value().codeVal).isNull();
        assertThat(row.value().modifiedAt).isEqualTo(nullable?null:"2026-11-14T00:00:00.123456Z");
    }
    @Test void groupProjectionUsesExplicitCountAndFlags() throws Exception {
        var config=configuration(); var mapping=config.getMappedStatement(CodeGroupMapper.class.getName()+".selectGroups").getResultMaps().get(0);
        var row=new CodeRows.Group(); var meta=config.newMetaObject(row);
        var labels=new LinkedHashMap<String,Object>();
        labels.put("code_grp_id","ALLOWANCE"); labels.put("code_grp_nm","수당"); labels.put("sys_code_yn","Y");
        labels.put("val_code_yn","N"); labels.put("sort_ord",76543); labels.put("code_count",234);
        assertThat(mapping.getAutoMapping()).isFalse();
        for(var field:mapping.getResultMappings()) meta.setValue(field.getProperty(),labels.get(field.getColumn()));
        assertThat(row.value()).isEqualTo(new com.company.common.dto.CodeContracts.Group("ALLOWANCE","수당","Y","N",234));
        assertThat(row.sortOrd).isEqualTo(76543);
    }
    @Test void allReferenceQueriesAreUnfilteredAndOnlyEnumIdentifiersAreInterpolated() throws Exception {
        var config=configuration(); var row=new CodeRows.Code(); row.groupId="JOB"; row.code="BE"; row.codeName="과거 이름";
        for(var ref:CodeReference.values()) {
            var parameters=java.util.Map.of("ref",ref,"row",row);
            var sql=config.getMappedStatement(CodeGroupMapper.class.getName()+".referenceExists").getBoundSql(parameters).getSql();
            assertThat(sql).contains(ref.getTable()).doesNotContain("del_yn", "use_yn", "close_stat_cd");
        }
        assertThat(com.company.common.config.MyBatisConfig.class
            .getAnnotation(org.mybatis.spring.annotation.MapperScan.class).basePackages())
            .contains(CodeGroupMapper.class.getPackageName());
    }
}
