package com.company.common.projects.mapper;

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

class ProjectProgressProjectionTest {
    Configuration config() throws Exception {
        var c=new Configuration();c.setMapUnderscoreToCamelCase(false);
        c.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String path="mapper/projects/ProjectProgressMapper.xml";
        try(var in=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(in,c,path,c.getSqlFragments()).parse();}
        return c;
    }
    @Test void everyMethodHasStatement() throws Exception {
        var c=config();for(var method:ProjectProgressMapper.class.getDeclaredMethods())
            assertThat(c.hasStatement(ProjectProgressMapper.class.getName()+"."+method.getName())).as(method.getName()).isTrue();
    }
    @Test void selectedLabelsMatchExplicitRecordConstructorsAndMaterializeNonDefaultValues() throws Exception {
        var c=config();var aliases=Pattern.compile("(?i)\\bAS\\s+\"([A-Za-z][A-Za-z0-9]*)\"");
        for(String method:List.of("owner","lockOwner","memos","steps","stageDefinitions")) {
            var statement=c.getMappedStatement(ProjectProgressMapper.class.getName()+"."+method);
            var mapping=statement.getResultMaps().get(0);var args=mapping.getConstructorResultMappings();
            var components=mapping.getType().getRecordComponents();assertThat(args).hasSize(components.length);
            var labels=new LinkedHashSet<String>();var match=aliases.matcher(statement.getBoundSql(Map.of("id","P-0001")).getSql());
            while(match.find()) labels.add(match.group(1));
            assertThat(labels).containsExactlyElementsOf(args.stream().map(a->a.getColumn()).toList());
            var values=new ArrayList<Object>();var types=new ArrayList<Class<?>>();
            for(int i=0;i<args.size();i++) {
                var type=components[i].getType();assertThat(args.get(i).getJavaType()).isEqualTo(type);
                assertThat(args.get(i).getColumn()).isEqualTo(components[i].getName());
                Object value=type==LocalDate.class?LocalDate.of(2026,11,15):type==Instant.class?Instant.parse("2026-11-15T00:00:00Z"):
                    type==BigDecimal.class?new BigDecimal("37.25"):type==Integer.class||type==int.class?37:"value:"+components[i].getName();
                assertThat(c.getTypeHandlerRegistry().getTypeHandler(type)).isNotNull();types.add(type);values.add(value);
            }
            Object record=c.getObjectFactory().create(mapping.getType(),types,values);
            for(int i=0;i<components.length;i++) assertThat(components[i].getAccessor().invoke(record)).isEqualTo(values.get(i));
        }
    }
    @Test void updatesPreserveWritingDateAndOriginalAuditAndNeverTouchProjectMoney() throws Exception {
        var c=config();
        String memo=c.getMappedStatement(ProjectProgressMapper.class.getName()+".updateMemo").getBoundSql(null).getSql();
        assertThat(memo).contains("MOD_USER_ID", "MOD_DTTM", "MEMO_SN=").doesNotContain("WRT_DT=", "REG_DTTM=", "REG_USER_ID=", "TB_PJ_PROJ");
        String dates=c.getMappedStatement(ProjectProgressMapper.class.getName()+".updateDates").getBoundSql(null).getSql();
        assertThat(dates).contains("CMPL_DT IS NULL").doesNotContain("STEP_WGHT_RT=", "PROJ_STEP_CD=", "TB_PJ_PROJ");
        assertThat(c.getMappedStatement(ProjectProgressMapper.class.getName()+".lockOwner").getBoundSql(Map.of("id","P-0001")).getSql()).contains("FOR UPDATE").doesNotContain("LOCK TABLE");
        String read=c.getMappedStatement(ProjectProgressMapper.class.getName()+".memos").getBoundSql(Map.of("id","P-0001")).getSql();
        assertThat(read).contains("m.REG_DTTM AS \"createdAt\"", "ORDER BY m.WRT_DT DESC, m.MEMO_SN DESC");
    }
}
