package com.company.common.billchecks.mapper;

import com.company.common.common.BillingNumberMapper;
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

class BillCheckProjectionTest {
    Configuration config()throws Exception {
        var config=new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        for(String resource:List.of("mapper/billchecks/BillCheckMapper.xml","mapper/common/BillingNumberMapper.xml")) {
            try(var stream=new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();
            }
        }
        return config;
    }
    @Test void registeredMappersHaveEveryStatement()throws Exception {
        var scan=com.company.common.config.MyBatisConfig.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
        var config=config();
        for(Class<?> mapper:List.of(BillCheckMapper.class,BillingNumberMapper.class)) {
            assertThat(scan.basePackages()).contains(mapper.getPackageName());
            for(var method:mapper.getDeclaredMethods()) assertThat(config.hasStatement(mapper.getName()+"."+method.getName())).isTrue();
        }
    }
    @Test void recordProjectionsHaveOrderedTypesAndEverySelectedLabel()throws Exception {
        var config=config();
        for(String statement:List.of("monthly","receipts")) {
            var mapped=config.getMappedStatement(BillCheckMapper.class.getName()+"."+statement);
            var mapping=mapped.getResultMaps().get(0);
            var components=mapping.getType().getRecordComponents();
            var arguments=mapping.getConstructorResultMappings();
            assertThat(arguments).hasSize(components.length);
            String sql=mapped.getBoundSql(new BillCheckSources.MonthFilter("202611",false)).getSql();
            var selected=labels(sql);
            Object[] values=new Object[components.length];
            for(int i=0;i<components.length;i++) {
                assertThat(arguments.get(i).getColumn()).isEqualTo(components[i].getName());
                assertThat(arguments.get(i).getJavaType()).isEqualTo(components[i].getType());
                assertThat(selected).contains(components[i].getName());
                values[i]=sample(components[i].getType(),components[i].getName());
                assertThat(config.getTypeHandlerRegistry().getTypeHandler(components[i].getType())).isNotNull();
            }
            Object result=mapping.getType().getDeclaredConstructors()[0].newInstance(values);
            for(int i=0;i<components.length;i++) assertThat(components[i].getAccessor().invoke(result)).isEqualTo(values[i]);
        }
    }
    @Test void invoiceBeanMapsEveryFieldThroughRealMybatisMetadataWithoutDatabase()throws Exception {
        var config=config();
        var mapped=config.getMappedStatement(BillCheckMapper.class.getName()+".invoice");
        var mapping=mapped.getResultMaps().get(0);
        var labels=labels(mapped.getBoundSql(Map.of("id","selected-bill")).getSql());
        var result=new BillCheckSources.Invoice();
        var meta=config.newMetaObject(result);
        assertThat(mapping.getResultMappings()).hasSize(result.getClass().getFields().length);
        for(var field:mapping.getResultMappings()) {
            assertThat(labels).contains(field.getColumn());
            assertThat(field.getJavaType()).isEqualTo(result.getClass().getField(field.getProperty()).getType());
            Object value=sample(field.getJavaType(),field.getProperty());
            meta.setValue(field.getProperty(),value);
            assertThat(meta.getValue(field.getProperty())).isEqualTo(value);
            assertThat(field.getTypeHandler()).isNotNull();
        }
    }
    Object sample(Class<?> type,String name) {
        return type==BigDecimal.class?new BigDecimal("123456.78")
            :type==LocalDate.class?LocalDate.of(2026,11,15)
            :type==Instant.class?Instant.parse("2026-11-15T01:00:00Z")
            :type==int.class?17:type==boolean.class?true:"value:"+name;
    }
    Set<String> labels(String sql) {
        var selected=new LinkedHashSet<String>();
        var matcher=Pattern.compile("(?i)\\bAS\\s+\"([a-zA-Z][a-zA-Z0-9]*)\"").matcher(sql);
        while(matcher.find()) selected.add(matcher.group(1));
        return selected;
    }
    @Test void allocationIncludesCancelledRowsAndIsProtectedEvenForEmptyMonth()throws Exception {
        var config=config();
        String prefix=BillingNumberMapper.class.getName()+".";
        assertThat(config.getMappedStatement(prefix+"lockBills").getBoundSql(Map.of()).getSql())
            .contains("LOCK TABLE TB_BL_BILL IN SHARE ROW EXCLUSIVE MODE");
        assertThat(config.getMappedStatement(prefix+"nextProjectOrder").getBoundSql(Map.of("projectId","selected")).getSql())
            .contains("MAX(b.BILL_ORD)").doesNotContain("CNCL_YN");
        assertThat(config.getMappedStatement(prefix+"nextMonthNumber").getBoundSql(Map.of("month","202611")).getSql())
            .contains("substring(b.BILL_ID FROM 10 FOR 3)").doesNotContain("CNCL_YN");
    }
    @Test void receiptsUseOriginalNumericIdentifierAndReceiptMonthClosure()throws Exception {
        String sql=config().getMappedStatement(BillCheckMapper.class.getName()+".receipts").getBoundSql(Map.of("id","selected")).getSql();
        assertThat(sql).contains("r.RCPT_ID::text AS \"receiptId\"","to_char(r.RCPT_DT,'YYYYMM')","ORDER BY r.RCPT_DT,r.RCPT_ID")
            .doesNotContain("RECEIPT_ID");
    }
    @Test void checkMonthsUseAnIndexableMonthRange()throws Exception {
        // IX_TB_BL_BILL_02(BILL_YM)를 쓸 수 있게 substring 대신 범위로 거른다.
        String sql=config().getMappedStatement(BillCheckMapper.class.getName()+".billMonths")
            .getBoundSql(Map.of("year","2026","until","202611")).getSql();
        assertThat(sql).contains("b.BILL_YM BETWEEN CAST(CAST(? AS text)||'01' AS CHAR(6)) AND CAST(CAST(? AS text)||'12' AS CHAR(6))")
            .doesNotContain("substring(b.BILL_YM");
    }
    @Test void correctionOverwritesOnlyTheVersionTheScreenRead()throws Exception {
        String sql=config().getMappedStatement(BillCheckMapper.class.getName()+".correct").getBoundSql(Map.of()).getSql();
        assertThat(sql).contains("COALESCE(MOD_DTTM,REG_DTTM) IS NOT DISTINCT FROM ?");
    }
}
