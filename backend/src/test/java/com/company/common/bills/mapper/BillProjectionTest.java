package com.company.common.bills.mapper;

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

class BillProjectionTest {
    Configuration config() throws Exception {
        var config=new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource="mapper/bills/BillMapper.xml";
        try(var stream=new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();
        }
        return config;
    }
    @Test void registeredMapperHasAllStatements() throws Exception {
        var scan=com.company.common.config.MyBatisConfig.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
        assertThat(scan.basePackages()).contains(BillMapper.class.getPackageName());
        var config=config();
        for(var method:BillMapper.class.getDeclaredMethods()) assertThat(config.hasStatement(BillMapper.class.getName()+"."+method.getName())).isTrue();
    }
    @Test void everyRecordProjectionHasExactConstructorTypesAndQuotedLabels() throws Exception {
        var config=config();
        for(String statement:List.of("invoice","project","receipts","monthly","recipients")) {
            var mapped=config.getMappedStatement(BillMapper.class.getName()+"."+statement);
            var map=mapped.getResultMaps().get(0);
            var fields=map.getType().getRecordComponents();
            var args=map.getConstructorResultMappings();
            assertThat(args).hasSize(fields.length);
            String sql=mapped.getBoundSql(new BillRows.RecipientFilter("project","202611",2)).getSql();
            Set<String> labels=new HashSet<>();
            var matcher=Pattern.compile("(?i)\\bAS\\s+\"([a-zA-Z][a-zA-Z0-9]*)\"").matcher(sql);
            while(matcher.find()) labels.add(matcher.group(1));
            Object[] values=new Object[fields.length];
            for(int i=0;i<fields.length;i++) {
                assertThat(args.get(i).getColumn()).isEqualTo(fields[i].getName());
                assertThat(args.get(i).getJavaType()).isEqualTo(fields[i].getType());
                assertThat(labels).contains(fields[i].getName());
                assertThat(args.get(i).getTypeHandler()).isNotNull();
                Class<?> type=fields[i].getType();
                values[i]=type==BigDecimal.class?new BigDecimal("321.45"):type==LocalDate.class?LocalDate.of(2026,11,15)
                    :type==Instant.class?Instant.parse("2026-11-15T01:00:00Z"):type==int.class?17:type==boolean.class?true:"value:"+fields[i].getName();
            }
            Object row=map.getType().getDeclaredConstructors()[0].newInstance(values);
            for(int i=0;i<fields.length;i++) assertThat(fields[i].getAccessor().invoke(row)).isEqualTo(values[i]);
        }
    }
    @Test void receiptIdentifierAndMonthUseOriginalSchema() throws Exception {
        String sql=config().getMappedStatement(BillMapper.class.getName()+".receipts").getBoundSql(Map.of("id","bill")).getSql();
        assertThat(sql).contains("r.RCPT_ID::text","to_char(r.RCPT_DT,'YYYYMM')").doesNotContain("RECEIPT_ID");
    }
    @Test void listOrderAndLatestUseOrderRatherThanMonth() throws Exception {
        String sql=config().getMappedStatement(BillMapper.class.getName()+".yearRows").getBoundSql(Map.of("year",2026)).getSql();
        // 가장 최근 청구 = 청구월이 가장 늦은 청구(시안 qbLatest), 차수 최대가 아님
        // 목록 기본 순서: 청구일 내림차순, 같은 날은 청구번호 오름차순(시안 sortDef). 엑셀도 같은 순서.
        assertThat(sql.strip()).endsWith("ORDER BY b.BILL_DT DESC,b.BILL_ID");
        assertThat(sql).contains("ORDER BY x.BILL_YM DESC,x.BILL_ID DESC LIMIT 1","x.CNCL_YN='N'")
            .doesNotContain("MAX(x.BILL_ORD)","x.BILL_DT DESC");
        // IX_TB_BL_BILL_02(BILL_YM)를 쓸 수 있게 substring 대신 범위로 거른다.
        assertThat(sql).contains("b.BILL_YM BETWEEN CAST(CAST(? AS text)||'01' AS CHAR(6)) AND CAST(CAST(? AS text)||'12' AS CHAR(6))")
            .doesNotContain("substring(b.BILL_YM");
    }
    @Test void updateDoesNotChangeIdentityOrReceiptRows() throws Exception {
        String sql=config().getMappedStatement(BillMapper.class.getName()+".update").getBoundSql(Map.of()).getSql();
        assertThat(sql).doesNotContain("SET PROJ_ID","BILL_YM=","BILL_ORD=","UPDATE TB_BL_RCPT");
    }
}
