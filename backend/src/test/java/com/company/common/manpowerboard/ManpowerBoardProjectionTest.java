package com.company.common.manpowerboard;

import com.company.common.manpowerboard.dto.ManpowerBoardContracts.Filter;
import com.company.common.manpowerboard.mapper.ManpowerBoardMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ManpowerBoardProjectionTest {
    Configuration configuration() throws Exception {
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String path = "mapper/manpowerboard/ManpowerBoardMapper.xml";
        try (var input = new ClassPathResource(path).getInputStream()) {
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    @Test void everyRecordProjectionHasExplicitOrderedConstructorAliasesAndHandlersWithoutDatabase() throws Exception {
        var configuration = configuration();
        for (String method : List.of("developers", "assignments", "monthly", "history", "statuses", "tentative", "codes")) {
            var statement = configuration.getMappedStatement(ManpowerBoardMapper.class.getName() + "." + method);
            var result = statement.getResultMaps().get(0);
            var components = result.getType().getRecordComponents();
            var mappings = result.getConstructorResultMappings();
            assertThat(mappings).hasSize(components.length);
            var aliases = new HashSet<String>();
            var matcher = Pattern.compile("(?i)\\bAS\\s+\"([a-zA-Z][a-zA-Z0-9]*)\"")
                .matcher(statement.getBoundSql(new Filter(2026, null, null, null, false)).getSql());
            while (matcher.find()) aliases.add(matcher.group(1));
            var values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                assertThat(mappings.get(i).getColumn()).isEqualTo(components[i].getName());
                assertThat(mappings.get(i).getJavaType()).isEqualTo(components[i].getType());
                assertThat(aliases).contains(components[i].getName());
                var type = components[i].getType();
                assertThat(configuration.getTypeHandlerRegistry().getTypeHandler(type)).isNotNull();
                values[i] = type == BigDecimal.class ? new BigDecimal("0.37")
                    : type == LocalDate.class ? LocalDate.of(2026, 11, 15)
                    : type == int.class ? 19 : "value:" + components[i].getName();
            }
            var row = result.getType().getDeclaredConstructors()[0].newInstance(values);
            for (int i = 0; i < components.length; i++) {
                assertThat(components[i].getAccessor().invoke(row)).isEqualTo(values[i]);
            }
        }
    }

    @Test void filtersAreDynamicAndBusinessSqlIsReadOnly() throws Exception {
        var configuration = configuration();
        for (String method : List.of("developers", "assignments", "monthly", "history", "statuses", "tentative")) {
            var statement = configuration.getMappedStatement(ManpowerBoardMapper.class.getName() + "." + method);
            var absent = statement.getBoundSql(new Filter(2026, null, null, null, false));
            assertThat(absent.getParameterMappings()).isEmpty();
            var present = statement.getBoundSql(new Filter(2027, "BE", "MD", "FT", true));
            assertThat(present.getParameterMappings()).extracting(p -> p.getProperty())
                .containsExactly("jobCode", "gradeCode", "employeeTypeCode");
            assertThat(absent.getSql()).doesNotContain("? IS NULL", "UPDATE", "DELETE", "INSERT");
        }
        var developers = configuration.getMappedStatement(ManpowerBoardMapper.class.getName() + ".developers")
            .getBoundSql(new Filter(2026, null, null, null, false)).getSql();
        assertThat(developers).doesNotContain("USE_YN='Y'");
        var access = configuration.getMappedStatement(ManpowerBoardMapper.class.getName() + ".insertAccess")
            .getBoundSql(Map.of("userId", "admin", "target", "board")).getSql();
        assertThat(access).contains("TB_SY_ACCS_LOG", "'UI-28'", "'DNLD'");
        var scan = com.company.common.config.MyBatisConfig.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
        assertThat(scan.basePackages()).contains(ManpowerBoardMapper.class.getPackageName());
    }
}
