package com.company.common.commonops;

import com.company.common.manpowercomparisons.dto.ManpowerComparisonContracts.Filter;
import com.company.common.manpowercomparisons.mapper.ManpowerComparisonMapper;
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

class ManpowerComparisonProjectionTest {
    Configuration config() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/manpowercomparisons/ManpowerComparisonMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }

    @Test void recordProjectionHasExactOrderedAliasesTypesHandlersAndNonDefaultValues() throws Exception {
        var config = config();
        var statement = config.getMappedStatement(ManpowerComparisonMapper.class.getName() + ".selectSources");
        var result = statement.getResultMaps().get(0);
        var components = result.getType().getRecordComponents();
        var arguments = result.getConstructorResultMappings();
        assertThat(arguments).hasSize(components.length);
        var aliases = new HashSet<String>();
        var matcher = Pattern.compile("(?i)\\bAS\\s+\"([a-zA-Z][a-zA-Z0-9]*)\"")
            .matcher(statement.getBoundSql(new Filter(2026,null,null)).getSql());
        while (matcher.find()) aliases.add(matcher.group(1));
        var values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            assertThat(arguments.get(i).getColumn()).isEqualTo(components[i].getName());
            assertThat(arguments.get(i).getJavaType()).isEqualTo(components[i].getType());
            assertThat(aliases).contains(components[i].getName());
            var type = components[i].getType();
            assertThat(config.getTypeHandlerRegistry().getTypeHandler(type)).isNotNull();
            values[i] = type == BigDecimal.class ? new BigDecimal("123.45")
                : type == LocalDate.class ? LocalDate.of(2026,11,15) : "value:" + components[i].getName();
        }
        var row = result.getType().getDeclaredConstructors()[0].newInstance(values);
        for (int i = 0; i < components.length; i++)
            assertThat(components[i].getAccessor().invoke(row)).isEqualTo(values[i]);
    }

    @Test void mapperScanAndDynamicOptionalPredicateMatchProduction() throws Exception {
        var scan = com.company.common.config.MyBatisConfig.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
        assertThat(scan.basePackages()).contains(ManpowerComparisonMapper.class.getPackageName());
        var statement = config().getMappedStatement(ManpowerComparisonMapper.class.getName() + ".selectSources");
        var absent = statement.getBoundSql(new Filter(2026,null,null));
        assertThat(absent.getParameterMappings()).noneMatch(p -> p.getProperty().equals("projectId"));
        assertThat(absent.getSql()).contains("UNION", "SUM(m.PLAN_MM)", "SUM(m.MON_MM)", "a.ASGN_STAT_CD!='CNCL'")
            .doesNotContain("? IS NULL", "FIX_YN", "PROJ_STAT_CD", "INSERT", "UPDATE", "DELETE");
        assertThat(statement.getBoundSql(new Filter(2026,"P",null)).getParameterMappings())
            .anyMatch(p -> p.getProperty().equals("projectId"));
        assertThat(config().getMappedStatements().stream().filter(s -> s instanceof org.apache.ibatis.mapping.MappedStatement)
            .map(s -> (org.apache.ibatis.mapping.MappedStatement)s))
            .allMatch(s -> s.getSqlCommandType() == org.apache.ibatis.mapping.SqlCommandType.SELECT);
    }
}
