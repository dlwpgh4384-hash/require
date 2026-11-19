package com.company.common.receipts.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ReceiptProjectionTest {
    Configuration config()throws Exception {
        var config=new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource="mapper/receipts/ReceiptMapper.xml";
        try(var stream=new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();
        }
        return config;
    }
    @Test void mapperScanAndEveryStatementAreRegistered()throws Exception {
        var scan=com.company.common.config.MyBatisConfig.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
        assertThat(scan.basePackages()).contains(ReceiptMapper.class.getPackageName());
        var config=config();
        for(var method:ReceiptMapper.class.getDeclaredMethods())
            assertThat(config.hasStatement(ReceiptMapper.class.getName()+"."+method.getName())).isTrue();
    }
    @Test void constructorLabelsTypesAndValuesMatchWithoutDatabase()throws Exception {
        var config=config();
        for(String name:List.of("lockBill","receipt","lockReceipt")) {
            var statement=config.getMappedStatement(ReceiptMapper.class.getName()+"."+name);
            var mapping=statement.getResultMaps().get(0);
            var components=mapping.getType().getRecordComponents();
            var arguments=mapping.getConstructorResultMappings();
            assertThat(arguments).hasSize(components.length);
            var selected=new HashSet<String>();
            var matcher=Pattern.compile("(?i)\\bAS\\s+\"([a-zA-Z][a-zA-Z0-9]*)\"")
                .matcher(statement.getBoundSql(Map.of("billId","chosen","receiptId","321")).getSql());
            while(matcher.find()) selected.add(matcher.group(1));
            Object[] values=new Object[components.length];
            for(int i=0;i<components.length;i++) {
                var component=components[i];
                var argument=arguments.get(i);
                assertThat(argument.getColumn()).isEqualTo(component.getName());
                assertThat(argument.getJavaType()).isEqualTo(component.getType());
                assertThat(selected).contains(component.getName());
                assertThat(argument.getTypeHandler()).isNotNull();
                Class<?> type=component.getType();
                values[i]=type==BigDecimal.class?new BigDecimal("12345")
                    :type==LocalDate.class?LocalDate.of(2026,10,20)
                    :type==Instant.class?Instant.parse("2026-11-01T01:02:03Z")
                    :type==boolean.class?true:"chosen:"+component.getName();
            }
            Object result=mapping.getType().getDeclaredConstructors()[0].newInstance(values);
            for(int i=0;i<components.length;i++)
                assertThat(components[i].getAccessor().invoke(result)).isEqualTo(values[i]);
        }
    }
    @Test void originalReceiptKeyAndGeneratedIdAreUsedAndCancellationIsConditional()throws Exception {
        var config=config();
        var prefix=ReceiptMapper.class.getName()+".";
        var receipt=config.getMappedStatement(prefix+"receipt").getBoundSql(Map.of("receiptId","123")).getSql();
        assertThat(receipt).contains("r.RCPT_ID::text", "r.RCPT_DT", "to_char(r.RCPT_DT,'YYYYMM')")
            .doesNotContain("RECEIPT_ID","BILL_YM");
        var insert=config.getMappedStatement(prefix+"insertReceipt");
        assertThat(insert.isDirtySelect()).isTrue();
        assertThat(insert.getBoundSql(new ReceiptMapper.ReceiptWrite("chosen",LocalDate.of(2026,10,20),BigDecimal.ONE,"admin"))
            .getSql()).contains("RETURNING RCPT_ID::text");
        var cancel=config.getMappedStatement(prefix+"cancel").getBoundSql(Map.of("receiptId","123","reason","정정","actor","admin"));
        assertThat(cancel.getSql()).contains("AND CNCL_YN='N'", "CNCL_RSN", "CNCL_USER_ID", "CNCL_DTTM").doesNotContain("DELETE");
        assertThat(config.getMappedStatement(prefix+"normalSum").getBoundSql(Map.of("billId","chosen")).getSql())
            .contains("r.CNCL_YN='N'");
    }
}
