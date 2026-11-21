package com.company.common.financeitems;

import com.company.common.financeitems.config.FinanceItemConfiguration;
import com.company.common.financeitems.mapper.FinanceItemMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class FinanceItemProjectionTest {
    Configuration config() throws Exception {
        var config=new Configuration(); config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource="mapper/financeitems/FinanceItemMapper.xml";
        try (var input=new ClassPathResource(resource).getInputStream()) { new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse(); }
        return config;
    }
    @Test void mapperScanAndEveryStatementExist() throws Exception {
        assertThat(FinanceItemConfiguration.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class).basePackages()).contains(FinanceItemMapper.class.getPackageName());
        var config=config(); for (var method:FinanceItemMapper.class.getDeclaredMethods()) assertThat(config.hasStatement(FinanceItemMapper.class.getName()+"."+method.getName())).isTrue();
    }
    @Test void explicitConstructorsMapEveryRecordFieldAndHaveTypedHandlersWithoutDatabase() throws Exception {
        var config=config();
        for (String id:List.of("item","loan","transfer","adjustment","card","estimate","payment","cardPayment","cash")) {
            var mapping=config.getResultMap(FinanceItemMapper.class.getName()+"."+id);
            var components=mapping.getType().getRecordComponents();
            assertThat(mapping.getConstructorResultMappings()).hasSize(components.length);
            Object[] values=new Object[components.length]; Class<?>[] types=new Class<?>[components.length];
            for (int n=0;n<components.length;n++) {
                var field=mapping.getConstructorResultMappings().get(n); var type=components[n].getType(); types[n]=type;
                assertThat(field.getJavaType()).isEqualTo(type); assertThat(field.getTypeHandler()).isNotNull();
                values[n]=type==BigDecimal.class ? new BigDecimal("12345") : type==LocalDate.class ? LocalDate.of(2026,2,28)
                    : type==Instant.class ? Instant.parse("2026-02-01T00:00:00Z") : type==boolean.class ? true : type==int.class ? 2026 : "column:"+field.getColumn();
            }
            Object row=mapping.getType().getConstructor(types).newInstance(values);
            for (int n=0;n<components.length;n++) assertThat(components[n].getAccessor().invoke(row)).isEqualTo(values[n]);
        }
    }
    @Test void physicalCardTablesAndVersionsFollowOriginalSchema() throws Exception {
        var config=config(); String namespace=FinanceItemMapper.class.getName()+".";
        assertThat(config.getMappedStatement(namespace+"cards").getBoundSql(Map.of("month","202602")).getSql()).contains("TB_FI_CARD_PAY","p.pay_amt","c.use_yn='Y' OR p.pay_amt>0");
        assertThat(config.getMappedStatement(namespace+"cardPayment").getBoundSql(Map.of("month","202602")).getSql()).contains("TB_FI_CARD_MON","cash_amt","edit_yn");
        for (String id:List.of("updateItem","updateLoan","updateTransfer","updateAdjustment","updateCard","updatePayment","updateEstimate","confirm","deleteItem","deleteLoan","deleteTransfer","deleteAdjustment"))
            assertThat(config.getMappedStatement(namespace+id).getBoundSql(null).getSql()).contains("IS NOT DISTINCT FROM");
        assertThat(config.getMappedStatement(namespace+"updateItem").getBoundSql(null).getSql()).doesNotContain("upld_id=","auto_fill_yn=");
    }
}
