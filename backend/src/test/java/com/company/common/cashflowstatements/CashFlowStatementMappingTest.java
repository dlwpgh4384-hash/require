package com.company.common.cashflowstatements;

import com.company.common.financeitems.dto.FinanceItemContracts.CashContribution;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Pay;
import java.math.BigDecimal;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class CashFlowStatementMappingTest {
    static final String NS = "com.company.common.cashflowstatements.mapper.CashFlowStatementMapper.";
    @Test void everyProjectionHasExplicitOrderedConstructorLabelsAndRealObjectFactoryMaterialization() throws Exception {
        var config = configuration();
        for (var entry : Map.of("receipts", CashContribution.class, "miscellaneousIncome", CashContribution.class, "payroll", Pay.class).entrySet()) {
            var statement = config.getMappedStatement(NS + entry.getKey());
            var mapping = statement.getResultMaps().get(0).getConstructorResultMappings();
            var fields = entry.getValue().getRecordComponents();
            assertThat(mapping).hasSize(fields.length);
            var types = new ArrayList<Class<?>>();
            var values = new ArrayList<Object>();
            String sql = statement.getBoundSql(Map.of("end", "202612")).getSql();
            for (int i = 0; i < fields.length; i++) {
                assertThat(mapping.get(i).getColumn()).isEqualTo(fields[i].getName());
                assertThat(mapping.get(i).getJavaType()).isEqualTo(fields[i].getType());
                assertThat(sql).contains("\"" + fields[i].getName() + "\"");
                types.add(mapping.get(i).getJavaType());
                values.add(fields[i].getType() == BigDecimal.class ? BigDecimal.valueOf(i + 11) : fields[i].getName());
            }
            var result = config.getObjectFactory().create(entry.getValue(), types, values);
            for (int i = 0; i < fields.length; i++) assertThat(fields[i].getAccessor().invoke(result)).isEqualTo(values.get(i));
        }
    }
    @Test void auditUsesUi21AndOnlyNamedBoundValues() throws Exception {
        var sql = configuration().getMappedStatement(NS + "insertAccess").getBoundSql(Map.of("userId", "admin", "target", "자금수지표"));
        assertThat(sql.getSql()).contains("'UI-21'", "'DNLD'", "clock_timestamp()").doesNotContain("UI-09");
        assertThat(sql.getParameterMappings()).extracting(p -> p.getProperty()).containsExactly("userId", "target", "userId");
    }
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        for (String path : List.of("mapper/personalcostprofits/PersonalCostProfitMapper.xml", "mapper/cashflowstatements/CashFlowStatementMapper.xml")) {
            var resource = new ClassPathResource(path);
            try (var input = resource.getInputStream()) {
                new XMLMapperBuilder(input, config, resource.getPath(), config.getSqlFragments()).parse();
            }
        }
        return config;
    }
}
