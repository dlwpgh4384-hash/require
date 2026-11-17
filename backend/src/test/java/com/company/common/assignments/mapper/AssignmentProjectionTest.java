package com.company.common.assignments.mapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class AssignmentProjectionTest {
    @Test void productionMapperScanIncludesAssignments() {
        var scan=com.company.common.config.MyBatisConfig.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
        assertThat(scan.basePackages()).contains(AssignmentMapper.class.getPackageName());
        assertThat(scan.annotationClass()).isEqualTo(org.apache.ibatis.annotations.Mapper.class);
    }
    Configuration config() throws Exception {
        var c=new Configuration();c.setMapUnderscoreToCamelCase(false);c.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource="mapper/assignments/AssignmentMapper.xml";
        try(var in=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(in,c,resource,c.getSqlFragments()).parse();}return c;
    }
    @Test void everyInterfaceOperationHasAMappedStatement() throws Exception {var c=config();for(var method:AssignmentMapper.class.getDeclaredMethods())assertThat(c.hasStatement(AssignmentMapper.class.getName()+"."+method.getName())).as(method.getName()).isTrue();}
    @Test void eachProjectionHasExplicitConstructorMappingAndEverySelectedLabelMatches() throws Exception {
        var c=config();var aliases=Pattern.compile("(?i)\\bAS\\s+\"([a-zA-Z][a-zA-Z0-9]*)\"");
        for(String statement:List.of("rows","history","candidates","person")) {
            var mapped=c.getMappedStatement(AssignmentMapper.class.getName()+"."+statement);var mapping=mapped.getResultMaps().get(0);
            var components=mapping.getType().getRecordComponents();var args=mapping.getConstructorResultMappings();
            assertThat(args).hasSize(components.length);var selected=new LinkedHashSet<String>();var matcher=aliases.matcher(mapped.getBoundSql(Map.of()).getSql());while(matcher.find())selected.add(matcher.group(1));
            var values=new Object[components.length];
            for(int i=0;i<components.length;i++) {
                assertThat(args.get(i).getColumn()).isEqualTo(components[i].getName());assertThat(args.get(i).getJavaType()).isEqualTo(components[i].getType());assertThat(selected).contains(components[i].getName());
                Class<?> t=components[i].getType();values[i]=t==LocalDate.class?LocalDate.of(2026,11,15):t==Instant.class?Instant.parse("2026-11-15T00:00:00Z"):t==BigDecimal.class?new BigDecimal("123.45"):t==int.class||t==Integer.class?17:t==boolean.class?true:"value:"+components[i].getName();
                assertThat(c.getTypeHandlerRegistry().getTypeHandler(t)).isNotNull();
            }
            Object projected=mapping.getType().getDeclaredConstructors()[0].newInstance(values);
            for(int i=0;i<components.length;i++)assertThat(components[i].getAccessor().invoke(projected)).isEqualTo(values[i]);
        }
    }
    String sql(String name,Object p) throws Exception {return config().getMappedStatement(AssignmentMapper.class.getName()+"."+name).getBoundSql(p).getSql();}
    @Test void candidateSourceAndRemovalNeverWriteAssignmentOrOpportunity() throws Exception {assertThat(sql("candidates",Map.of())).contains("OPP_STAT_CD='WIN'","TENT_STAT_CD='CNVT'","n.NEED_SN=t.NEED_SN");assertThat(sql("removeCandidate",Map.of("id","12","actor","admin"))).contains("TENT_STAT_CD='DROP'","PROC_DTTM=clock_timestamp()").doesNotContain("DELETE","TB_PJ_ASGN","UPDATE TB_SL_OPP ");}
    @Test void openMonthDeletionIsBoundedAndFixedSnapshotsNeverChanged() throws Exception {assertThat(sql("deleteOpenMonths",new AssignmentRows.OpenRange("A-0001","202611"))).contains("INPUT_YM >=","FIX_YN='N'","CLOSE_STAT_CD='CLOSE'");assertThat(sql("deleteOpenCosts",new AssignmentRows.OpenRange("A-0001","202611"))).contains("COST_YM >=","FIX_YN='N'","CLOSE_STAT_CD='CLOSE'");}
    @Test void assignmentVersionAndHistoryCorrectionUseRealSourceTokens() throws Exception {var p=new HashMap<String,Object>();p.put("row",null);p.put("token",null);assertThat(sql("update",p)).contains("COALESCE(MOD_DTTM,REG_DTTM) IS NOT DISTINCT FROM");assertThat(sql("correct",new AssignmentRows.Correction("A-0001",1,null))).contains("CRCT_YN='N'","REG_DTTM IS NOT DISTINCT FROM").doesNotContain("MOD_DTTM","SET REG_DTTM");}
    @Test void effectiveHistoryIncludesCorrectedOriginalsAndOrderingIsStable() throws Exception {assertThat(sql("history",Map.of("id","A-0001"))).contains("ORDER BY REG_DTTM DESC,HIST_SN DESC").doesNotContain("CRCT_YN='N'");assertThat(sql("rows",Map.of())).contains("ORDER BY a.INPUT_BGN_DT ASC,a.ASGN_ID ASC");}
}
