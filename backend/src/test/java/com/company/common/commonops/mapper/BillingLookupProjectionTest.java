package com.company.common.commonops.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class BillingLookupProjectionTest {
    Configuration config()throws Exception {
        var config=new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource="mapper/commonops/BillingLookupMapper.xml";
        try(var stream=new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();
        }
        return config;
    }
    @Test void mapperScanAndStatementsAreRegistered()throws Exception {
        var scan=com.company.common.config.MyBatisConfig.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
        assertThat(scan.basePackages()).contains(BillingLookupMapper.class.getPackageName());
        var config=config();
        for(var method:BillingLookupMapper.class.getDeclaredMethods())
            assertThat(config.hasStatement(BillingLookupMapper.class.getName()+"."+method.getName())).isTrue();
    }
    @Test void everyRecordHasExplicitOrderedLabelsTypesAndNonDefaultValuesWithoutDatabase()throws Exception {
        var config=config();
        for(String name:List.of("projects","statuses","monthly","overdue","history","recipientRoles")) {
            var statement=config.getMappedStatement(BillingLookupMapper.class.getName()+"."+name);
            var mapping=statement.getResultMaps().get(0);
            var components=mapping.getType().getRecordComponents();
            var arguments=mapping.getConstructorResultMappings();
            assertThat(arguments).hasSize(components.length);
            var selected=new HashSet<String>();
            var matcher=Pattern.compile("(?i)\\bAS\\s+\"([a-zA-Z][a-zA-Z0-9]*)\"").matcher(
                statement.getBoundSql(Map.of("projectId","selected","month","202611","today",LocalDate.of(2026,11,15))).getSql());
            while(matcher.find()) selected.add(matcher.group(1));
            Object[] values=new Object[components.length];
            for(int i=0;i<components.length;i++) {
                var component=components[i]; var argument=arguments.get(i);
                assertThat(argument.getColumn()).isEqualTo(component.getName());
                assertThat(argument.getJavaType()).isEqualTo(component.getType());
                assertThat(selected).contains(component.getName());
                assertThat(argument.getTypeHandler()).isNotNull();
                Class<?> type=component.getType();
                values[i]=type==BigDecimal.class?new BigDecimal("1234.56"):type==LocalDate.class?LocalDate.of(2025,10,20)
                    :type==int.class?23:type==boolean.class?true:"selected:"+component.getName();
            }
            Object result=mapping.getType().getDeclaredConstructors()[0].newInstance(values);
            for(int i=0;i<components.length;i++) assertThat(components[i].getAccessor().invoke(result)).isEqualTo(values[i]);
        }
    }
    @Test void optionalProjectAddsOnlyNonNullPredicateAndBinding()throws Exception {
        var statement=config().getMappedStatement(BillingLookupMapper.class.getName()+".projects");
        var absent=statement.getBoundSql(new BillingLookupMapper.OptionFilter(null,"202611"));
        var present=statement.getBoundSql(new BillingLookupMapper.OptionFilter("selected","202611"));
        assertThat(absent.getParameterMappings()).isEmpty();
        assertThat(present.getParameterMappings()).extracting(p -> p.getProperty()).containsExactly("projectId");
        assertThat(present.getSql()).contains("p.PROJ_ID=?");
    }
    @Test void dunningQueriesShareYearIndependentPredicateAndDeterministicOrder()throws Exception {
        var config=config();
        String prefix=BillingLookupMapper.class.getName()+".";
        String rows=config.getMappedStatement(prefix+"overdue").getBoundSql(Map.of("today",LocalDate.of(2026,11,15))).getSql();
        String history=config.getMappedStatement(prefix+"history").getBoundSql(Map.of("today",LocalDate.of(2026,11,15))).getSql();
        for(String sql:List.of(rows,history)) assertThat(sql).contains("b.CNCL_YN='N' AND b.UNPAID_AMT>0 AND b.PAY_DUE_DT<?")
            .doesNotContain("BILL_YM", "CREATE", "UPDATE", "INSERT", "DELETE");
        assertThat(rows).contains("ORDER BY b.BILL_DT DESC,b.BILL_ORD DESC,b.BILL_ID DESC");
        assertThat(history).contains("ORDER BY h.SEND_DT ASC,h.DUN_SN ASC");
    }
}
