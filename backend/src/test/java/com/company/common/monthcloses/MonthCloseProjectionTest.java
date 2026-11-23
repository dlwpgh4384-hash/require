package com.company.common.monthcloses;

import com.company.common.monthcloses.dto.MonthCloseContracts.*;
import com.company.common.monthcloses.mapper.MonthCloseMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class MonthCloseProjectionTest {
    Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/monthcloses/MonthCloseMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }
    @Test void explicitHeaderConstructorAndQuotedLabelsMatchEveryRecordComponent() throws Exception {
        var token = Instant.parse("2026-01-31T15:00:01.123456Z");
        var labels = new LinkedHashMap<String, Object>();
        labels.put("state", "CLOSE"); labels.put("modifiedAt", token);
        labels.put("closedAt", token.minusSeconds(1)); labels.put("closedBy", "저장 처리자");
        var value = materialize("selectHeader", labels, "202601");
        assertThat(value).isEqualTo(new Header("CLOSE", token, token.minusSeconds(1), "저장 처리자"));
    }
    @Test void countsProjectionHasNoDefaultedOrFoldedLabels() throws Exception {
        var labels = new LinkedHashMap<String, Object>();
        labels.put("payroll", 7); labels.put("invoices", 3); labels.put("finance", 5);
        assertThat(materialize("selectCounts", labels, "202601")).isEqualTo(new Counts(7, 3, 5));
    }
    Object materialize(String method, LinkedHashMap<String, Object> labels, String month) throws Exception {
        var config = configuration();
        var statement = config.getMappedStatement(MonthCloseMapper.class.getName() + "." + method);
        var mapping = statement.getResultMaps().get(0);
        assertThat(mapping.getAutoMapping()).isFalse();
        assertThat(mapping.getConstructorResultMappings()).extracting(field -> field.getColumn()).containsExactlyElementsOf(labels.keySet());
        var components = mapping.getType().getRecordComponents();
        var fields = mapping.getConstructorResultMappings();
        for (int i = 0; i < components.length; i++) {
            assertThat(fields.get(i).getJavaType()).isEqualTo(components[i].getType());
            assertThat(fields.get(i).getColumn()).isEqualTo(components[i].getName());
            assertThat(fields.get(i).getTypeHandler()).isNotNull();
            assertThat(statement.getBoundSql(java.util.Map.of("month", month)).getSql()).contains("\"" + components[i].getName() + "\"");
        }
        return mapping.getType().getDeclaredConstructors()[0].newInstance(labels.values().toArray());
    }
    @Test void optimisticWriteBindingsAndHistoryNaturalIdentityAreExplicit() throws Exception {
        var config = configuration();
        var command = new Mutation("202601", "CLOSE", "admin", null);
        var statement = config.getMappedStatement(MonthCloseMapper.class.getName() + ".saveClose");
        var bound = statement.getBoundSql(command);
        for (var param : bound.getParameterMappings()) assertThat(config.newMetaObject(command).hasGetter(param.getProperty())).isTrue();
        assertThat(bound.getSql()).contains("IS NOT DISTINCT FROM", "GREATEST", "interval '1 microsecond'");
        var history = config.getMappedStatement(MonthCloseMapper.class.getName() + ".insertHistory");
        assertThat(history.getBoundSql(new History("202601", "OPEN", "사유", null, "admin")).getSql())
            .contains("CLOSE_YM, HIST_SN, ACT_CD, ACT_RSN, REMAIN_CHK_CN", "max(HIST_SN)");
    }
}
