package com.company.common.withdrawals.mapper;

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

class WithdrawalProjectionTest {
    Configuration config() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/withdrawals/WithdrawalMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }

    @Test void mapperIsRegisteredAndEveryMethodHasAStatement() throws Exception {
        var scan = com.company.common.config.MyBatisConfig.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
        assertThat(scan.basePackages()).contains(WithdrawalMapper.class.getPackageName());
        var config = config();
        for (var method : WithdrawalMapper.class.getDeclaredMethods()) {
            assertThat(config.hasStatement(WithdrawalMapper.class.getName() + "." + method.getName())).isTrue();
        }
    }

    @Test void everyProjectionHasExplicitOrderedTypesLabelsAndNonDefaultValues() throws Exception {
        var config = config();
        var aliases = Pattern.compile("(?i)\\bAS\\s+\"([a-zA-Z][a-zA-Z0-9]*)\"");
        var filter = new WithdrawalRows.MonthFilter("BE", "202612", LocalDate.of(2026,12,1), LocalDate.of(2026,12,31));
        for (String statement : List.of("source", "needs", "reservations", "supply", "extensions", "opportunityDemand", "projectDemand")) {
            var mapped = config.getMappedStatement(WithdrawalMapper.class.getName() + "." + statement);
            var mapping = mapped.getResultMaps().get(0);
            var components = mapping.getType().getRecordComponents();
            var arguments = mapping.getConstructorResultMappings();
            assertThat(arguments).hasSize(components.length);
            var selected = new LinkedHashSet<String>();
            var matcher = aliases.matcher(mapped.getBoundSql(filter).getSql());
            while (matcher.find()) selected.add(matcher.group(1));
            var values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                assertThat(arguments.get(i).getColumn()).isEqualTo(components[i].getName());
                assertThat(arguments.get(i).getJavaType()).isEqualTo(components[i].getType());
                assertThat(selected).contains(components[i].getName());
                Class<?> type = components[i].getType();
                values[i] = type == LocalDate.class ? LocalDate.of(2026,11,15)
                    : type == Instant.class ? Instant.parse("2026-11-15T00:00:00Z")
                    : type == BigDecimal.class ? new BigDecimal("123.45")
                    : type == int.class ? 17 : "value:" + components[i].getName();
                assertThat(config.getTypeHandlerRegistry().getTypeHandler(type)).isNotNull();
            }
            Object row = mapping.getType().getDeclaredConstructors()[0].newInstance(values);
            for (int i = 0; i < components.length; i++) assertThat(components[i].getAccessor().invoke(row)).isEqualTo(values[i]);
        }
    }

    String sql(String name, Object parameters) throws Exception {
        return config().getMappedStatement(WithdrawalMapper.class.getName() + "." + name).getBoundSql(parameters).getSql();
    }

    @Test void optionalFiltersBindOnlyPresentValuesAndUnknownExtensionMeansSqlNull() throws Exception {
        assertThat(sql("list", new WithdrawalRows.ListFilter(2026, null, null, null, null)))
            .doesNotContain("a.JOB_CD=?", "a.EXT_PSBL_CD=?", "? IS NULL");
        assertThat(sql("list", new WithdrawalRows.ListFilter(2026, LocalDate.of(2026,11,15), LocalDate.of(2027,1,31), "", "BE")))
            .contains("a.EXT_PSBL_CD IS NULL", "a.JOB_CD=?", "a.INPUT_END_DT >=", "ORDER BY a.INPUT_END_DT,a.ASGN_ID");
        assertThat(sql("shared", new WithdrawalRows.SharedFilter("person", "opportunity")))
            .contains("a.DEV_ID=?", "t.OPP_ID=?", "'ACT','CNVT'");
    }

    @Test void reservationsUseCompositeIdentityAndOnlyCurrentStates() throws Exception {
        assertThat(sql("needs", Map.of())).contains("t.OPP_ID=n.OPP_ID AND t.NEED_SN=n.NEED_SN", "'ACT','CNVT'");
        assertThat(sql("lockNeeds", Map.of())).contains("ORDER BY n.OPP_ID,n.NEED_SN FOR UPDATE OF n,o");
        assertThat(sql("release", Map.of("id", "12", "actor", "admin")))
            .contains("TENT_STAT_CD='REL'", "PROC_DTTM=clock_timestamp()", "TENT_STAT_CD='ACT'").doesNotContain("DELETE");
    }
}
