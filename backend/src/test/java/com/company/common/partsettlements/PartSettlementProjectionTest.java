package com.company.common.partsettlements;

import com.company.common.partsettlements.config.PartSettlementConfiguration;
import com.company.common.partsettlements.dto.PartSettlementContracts.*;
import com.company.common.partsettlements.mapper.PartSettlementMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class PartSettlementProjectionTest {
    Configuration config() throws Exception {
        var config=new Configuration();config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource="mapper/partsettlements/PartSettlementMapper.xml";
        try(var input=new ClassPathResource(resource).getInputStream()) {new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        return config;
    }
    @Test void featureMapperIsRegisteredAndAllStatementsExist() throws Exception {
        var scan=PartSettlementConfiguration.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
        assertThat(scan.basePackages()).contains(PartSettlementMapper.class.getPackageName());
        var config=config();for(var method:PartSettlementMapper.class.getDeclaredMethods()) assertThat(config.hasStatement(PartSettlementMapper.class.getName()+"."+method.getName())).isTrue();
    }
    @Test void everyMutableProjectionMapsEveryFieldWithoutDatabase() throws Exception {
        var config=config();
        for(String id:List.of("part","header","input","history","detail")) {
            var mapping=config.getResultMap(PartSettlementMapper.class.getName()+"."+id);
            var row=mapping.getType().getConstructor().newInstance();var meta=config.newMetaObject(row);
            assertThat(mapping.getResultMappings()).hasSize(row.getClass().getFields().length);
            for(var field:mapping.getResultMappings()) {
                var type=row.getClass().getField(field.getProperty()).getType();assertThat(field.getJavaType()).isEqualTo(type);
                Object value=type==BigDecimal.class?new BigDecimal("123.45"):type==LocalDate.class?LocalDate.of(2026,1,16)
                    :type==Instant.class?Instant.parse("2026-01-16T00:00:00Z"):type==Boolean.class?Boolean.TRUE:"column:"+field.getColumn();
                meta.setValue(field.getProperty(),value);assertThat(meta.getValue(field.getProperty())).isEqualTo(value);assertThat(field.getTypeHandler()).isNotNull();
            }
        }
    }
    @Test void mutationsBindDeclaredPropertiesAndNullSafeVersion() throws Exception {
        var config=config();var mutation=new Mutation(new Key("A","202601"),null,new BigDecimal("123"),new BigDecimal("456"),"CNFM","admin");
        for(String id:List.of("updateCard","confirm")) {
            var sql=config.getMappedStatement(PartSettlementMapper.class.getName()+"."+id).getBoundSql(mutation);
            assertThat(sql.getSql()).contains("IS NOT DISTINCT FROM","clock_timestamp()");
            var meta=config.newMetaObject(mutation);
            for(var parameter:sql.getParameterMappings()) assertThat(meta.hasGetter(parameter.getProperty())).isTrue();
        }
    }
    @Test void snapshotUsesPhysicalColumnsAndBoundJoinFormats() throws Exception {
        var config=config();var rules=new PartSettlementRulesTest();rules.setup();
        var row=rules.rules.calculate(PartSettlementTestData.part("A"),YearMonth.of(2026,1),List.of(PartSettlementTestData.input("D","A")),List.of()).get(0);
        var snapshot=new Snapshot(new Key("A","202601"),row,"admin");
        var sql=config.getMappedStatement(PartSettlementMapper.class.getName()+".insertDetail").getBoundSql(snapshot);
        assertThat(sql.getSql()).contains("diff_amt","emp_type_cd","proj_nm_list");
        for(var parameter:sql.getParameterMappings()) assertThat(config.newMetaObject(snapshot).hasGetter(parameter.getProperty())).isTrue();
        var meta=config.newMetaObject(snapshot);
        assertThat(meta.getValue("projectNames")).isEqualTo(snapshot.getProjectNames());
        assertThat(meta.getValue("notes")).isEqualTo(snapshot.getNotes());
        assertThat(snapshot.getProjectNames()).isEqualTo("프로젝트");assertThat(snapshot.getNotes()).contains("공통관리비+퇴직금");
    }
}
