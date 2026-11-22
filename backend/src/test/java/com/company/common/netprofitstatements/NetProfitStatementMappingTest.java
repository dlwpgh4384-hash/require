package com.company.common.netprofitstatements;

import com.company.common.profitstructures.mapper.ProfitStructureRows.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class NetProfitStatementMappingTest {
    @Test void sharedSourceRecordsMatchExplicitConstructorMappingsWithoutDatabase() throws Exception {
        var config = configuration();
        for (var entry : Map.of("snapshots", Snapshot.class, "projects", ProjectMonth.class).entrySet()) {
            var statement = config.getMappedStatement("com.company.common.profitstructures.mapper.ProfitStructureMapper." + entry.getKey());
            var mappings = statement.getResultMaps().get(0).getConstructorResultMappings();
            var components = entry.getValue().getRecordComponents();
            assertThat(mappings).hasSize(components.length);
            var types = new ArrayList<Class<?>>();
            var values = new ArrayList<Object>();
            String sql = statement.getBoundSql(new Query(2026, 2)).getSql();
            for (int i = 0; i < components.length; i++) {
                var mapping = mappings.get(i);
                var component = components[i];
                assertThat(mapping.getColumn()).isEqualTo(component.getName());
                assertThat(mapping.getJavaType()).isEqualTo(component.getType());
                assertThat(sql).contains("\"" + component.getName() + "\"");
                types.add(mapping.getJavaType());
                values.add(component.getType() == BigDecimal.class ? BigDecimal.valueOf(i + 1)
                    : component.getType() == Instant.class ? Instant.parse("2026-01-01T01:02:03Z") : component.getName());
            }
            var row = config.getObjectFactory().create(entry.getValue(), types, values);
            for (int i = 0; i < components.length; i++) {
                assertThat(components[i].getAccessor().invoke(row)).isEqualTo(values.get(i));
            }
        }
    }
    @Test void auditStatementBindsBothParametersAndOwnScreenIdentity() throws Exception {
        var config = configuration();
        var statement = config.getMappedStatement("com.company.common.netprofitstatements.mapper.NetProfitStatementMapper.insertAccess");
        var sql = statement.getBoundSql(Map.of("userId", "admin", "target", "순이익표"));
        assertThat(sql.getSql()).contains("'UI-09'", "'DNLD'", "clock_timestamp()").doesNotContain("UI-08", "UI-07");
        assertThat(sql.getParameterMappings()).extracting(p -> p.getProperty())
            .containsExactly("userId", "target", "userId");
    }
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        for (String path : List.of("mapper/personalcostprofits/PersonalCostProfitMapper.xml",
            "mapper/profitstructures/ProfitStructureMapper.xml", "mapper/netprofitstatements/NetProfitStatementMapper.xml")) {
            var resource = new ClassPathResource(path);
            try (var input = resource.getInputStream()) {
                new XMLMapperBuilder(input, config, resource.getPath(), config.getSqlFragments()).parse();
            }
        }
        return config;
    }
}
