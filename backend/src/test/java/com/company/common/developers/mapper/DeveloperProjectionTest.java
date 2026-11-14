package com.company.common.developers.mapper;

import com.company.common.developers.dto.DeveloperContracts.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class DeveloperProjectionTest {
    private Configuration config() throws Exception {
        var c=new Configuration(); c.setMapUnderscoreToCamelCase(false);
        c.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource="mapper/developers/DeveloperMapper.xml";
        try(var in=new ClassPathResource(resource).getInputStream()) { new XMLMapperBuilder(in,c,resource,c.getSqlFragments()).parse(); }
        return c;
    }
    @Test void everySelectedFieldHasExplicitTypeAndMaterializesWithoutDatabase() throws Exception {
        var c=config();
        for(String id:List.of("developer","assignment","state","area","address","code","change")) {
            var map=c.getResultMap(DeveloperMapper.class.getName()+"."+id); assertThat(map.getAutoMapping()).isFalse();
            var object=map.getType().getDeclaredConstructor().newInstance(); var meta=c.newMetaObject(object);
            for(var field:map.getResultMappings()) {
                Class<?> type=field.getJavaType(); Object value=type==LocalDate.class?LocalDate.of(2026,11,15):
                    type==Instant.class?Instant.parse("2026-11-14T16:00:00Z"):type==BigDecimal.class?new BigDecimal("123.45"):
                    type==int.class || type==Integer.class?17:"value:"+field.getProperty();
                assertThat(field.getTypeHandler()).isNotNull(); assertThat(meta.getSetterType(field.getProperty())).isEqualTo(type);
                meta.setValue(field.getProperty(),value);assertThat(meta.getValue(field.getProperty())).isEqualTo(value);
            }
        }
        for(var method:DeveloperMapper.class.getDeclaredMethods()) assertThat(c.hasStatement(DeveloperMapper.class.getName()+"."+method.getName())).isTrue();
    }
    @Test void filtersOnlyBindPresentValuesAndNoPaginationIsAdded() throws Exception {
        var c=config(); var params=new HashMap<String,Object>();params.put("month","202611");params.put("filter",new Filter(null,null,null,null,null,false));
        var statement=c.getMappedStatement(DeveloperMapper.class.getName()+".selectDevelopers");
        var emptyBound=statement.getBoundSql(params);String empty=emptyBound.getSql();
        assertThat(empty).doesNotContain("LIMIT","OFFSET","strpos").doesNotContainPattern("(?i)\\?\\s+IS\\s+NULL\\s+OR\\b");
        assertThat(empty).contains("r.aply_end_ym IS NULL OR r.aply_end_ym >= ?");
        assertThat(emptyBound.getParameterMappings()).extracting(mapping -> mapping.getProperty()).containsExactly("month","month");
        params.put("filter",new Filter("FT","DEV","휴직","DRCT","이름",true));var filteredBound=statement.getBoundSql(params);String filtered=filteredBound.getSql();
        assertThat(filtered).contains("d.emp_type_cd=","d.job_cd=","d.sub_job_cd=","d.drct_cls_cd=","strpos","ORDER BY d.dev_nm,d.dev_id");
        assertThat(filtered).doesNotContain("LIMIT","OFFSET").doesNotContainPattern("(?i)\\?\\s+IS\\s+NULL\\s+OR\\b");
        assertThat(filteredBound.getParameterMappings()).extracting(mapping -> mapping.getProperty()).containsExactly(
            "month","month","filter.employeeTypeCode","filter.jobCode","filter.jobCode","filter.directClassCode","filter.q","filter.q");
    }
    @Test void priceUpsertPreservesNextPeriodAndNeverTouchesPayroll() throws Exception {
        var c=config();var p=new PriceWrite("dev","202611","202610",BigDecimal.TEN,BigDecimal.ONE,"admin");
        String sql=c.getMappedStatement(DeveloperMapper.class.getName()+".savePrice").getBoundSql(p).getSql();
        assertThat(sql).contains("ON CONFLICT(dev_id,aply_bgn_ym)","min(aply_bgn_ym)").doesNotContain("TB_HR_MON_PAY");
    }
    @Test void numericSequencesAreExactAndNullable() throws Exception {
        var result=org.mockito.Mockito.mock(java.sql.ResultSet.class);var h=new DeveloperSequenceTypeHandler();
        org.mockito.Mockito.when(result.getBigDecimal("sn")).thenReturn(new BigDecimal("17")); assertThat(h.getNullableResult(result,"sn")).isEqualTo(17);
        org.mockito.Mockito.when(result.getBigDecimal("sn")).thenReturn(new BigDecimal("1.2")); assertThatThrownBy(() -> h.getNullableResult(result,"sn")).isInstanceOf(java.sql.SQLException.class);
        org.mockito.Mockito.when(result.getBigDecimal("sn")).thenReturn(null);assertThat(h.getNullableResult(result,"sn")).isNull();
    }
}
