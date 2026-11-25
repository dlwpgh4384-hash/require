package com.company.common.manpowerforecasts;

import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.mapper.SalesPipelineRows.MonthWindow;
import com.company.common.manpowerforecasts.mapper.ManpowerForecastMapper;
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

class ManpowerForecastProjectionTest {
    Configuration configuration() throws Exception {
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        for (String path : List.of("mapper/salespipeline/SalesPipelineMapper.xml", "mapper/manpowerforecasts/ManpowerForecastMapper.xml")) {
            try (var input = new ClassPathResource(path).getInputStream()) {
                new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }
    @Test void sharedWorkforceRecordProjectionsMatchExplicitConstructorsAliasesAndHandlersWithoutDatabase() throws Exception {
        var configuration = configuration();
        var parameters = Map.of("months", List.of(new MonthWindow("202612", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))),
            "baseMonth", LocalDate.of(2026, 11, 1), "horizonStart", LocalDate.of(2026, 12, 1));
        for (String method : List.of("jobs", "supply", "extensions", "opportunityDemand", "projectDemand")) {
            var statement = configuration.getMappedStatement(SalesPipelineMapper.class.getName() + "." + method);
            var result = statement.getResultMaps().get(0);
            var components = result.getType().getRecordComponents();
            var mappings = result.getConstructorResultMappings();
            assertThat(mappings).hasSize(components.length);
            var aliases = new HashSet<String>();
            var matcher = Pattern.compile("(?i)\\bAS\\s+\"([a-zA-Z][a-zA-Z0-9]*)\"").matcher(statement.getBoundSql(parameters).getSql());
            while (matcher.find()) aliases.add(matcher.group(1));
            var values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                assertThat(mappings.get(i).getColumn()).isEqualTo(components[i].getName());
                assertThat(mappings.get(i).getJavaType()).isEqualTo(components[i].getType());
                assertThat(aliases).contains(components[i].getName());
                var type = components[i].getType();
                assertThat(configuration.getTypeHandlerRegistry().getTypeHandler(type)).isNotNull();
                values[i] = type == BigDecimal.class ? new BigDecimal("0.37") : type == int.class ? 19 : "value:" + components[i].getName();
            }
            var row = result.getType().getDeclaredConstructors()[0].newInstance(values);
            for (int i = 0; i < components.length; i++) assertThat(components[i].getAccessor().invoke(row)).isEqualTo(values[i]);
        }
    }
    @Test void onlyFeatureWriteIsUi17DownloadWithDatabaseEventTime() throws Exception {
        var sql = configuration().getMappedStatement(ManpowerForecastMapper.class.getName() + ".insertAccess")
            .getBoundSql(Map.of("userId", "admin", "count", 3)).getSql();
        assertThat(sql).contains("TB_SY_ACCS_LOG", "'DNLD'", "'UI-17'", "clock_timestamp()")
            .doesNotContain("'UI-29'", "TB_PJ_MNPW_PLAN", "TB_PJ_ASGN", "TB_SY_OPER_STD");
    }
}
