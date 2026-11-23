package com.company.common.commonops;

import com.company.common.commonops.mapper.BusinessPlanComparisonRows.Target;
import java.math.BigDecimal;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class BusinessPlanComparisonMappingTest {
    @Test void explicitConstructorAndQuotedLabelsMatchProductionMappingWithoutDatabase() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        var resource = new ClassPathResource("mapper/commonops/BusinessPlanComparisonMapper.xml");
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, config, resource.getPath(), config.getSqlFragments()).parse();
        }
        var statement = config.getMappedStatement("com.company.common.commonops.mapper.BusinessPlanComparisonMapper.targets");
        var mappings = statement.getResultMaps().get(0).getConstructorResultMappings();
        var components = Target.class.getRecordComponents();
        assertThat(mappings).hasSize(components.length);
        var types = new ArrayList<Class<?>>();
        var values = new ArrayList<Object>();
        String sql = statement.getBoundSql(Map.of("year", 2026)).getSql();
        for (int i = 0; i < components.length; i++) {
            var mapping = mappings.get(i);
            var component = components[i];
            assertThat(mapping.getColumn()).isEqualTo(component.getName());
            assertThat(mapping.getJavaType()).isEqualTo(component.getType());
            assertThat(sql).contains("\"" + component.getName() + "\"");
            types.add(mapping.getJavaType());
            values.add(component.getType() == BigDecimal.class ? BigDecimal.valueOf(i * 123L) : "202611");
        }
        var row = config.getObjectFactory().create(Target.class, types, values);
        for (int i = 0; i < components.length; i++)
            assertThat(components[i].getAccessor().invoke(row)).isEqualTo(values.get(i));
        assertThat(sql).contains("PART_ID = 'ALL'").doesNotContain("UPDATE", "INSERT", "DELETE");
    }
}
