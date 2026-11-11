package com.company.common.userpreferences;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class UserPreferenceProjectionTest {
    @Test
    void actualLabelsAndExplicitMappingPreserveMicrosecondsWithoutDatabase() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        try (var input = new ClassPathResource("mapper/UserPreferenceMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "mapper/UserPreferenceMapper.xml", config.getSqlFragments()).parse();
        }
        var statement = config.getMappedStatement(UserPreferenceMapper.class.getName() + ".selectPreference");
        var map = statement.getResultMaps().get(0);
        assertThat(map.getAutoMapping()).isFalse();
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("pref_val", "50");
        labels.put("mod_dttm", LocalDateTime.of(2026, 2, 3, 4, 5, 6, 123456000));
        String sql = statement.getBoundSql(Map.of("type", "PAGE_SIZE", "key", "GLOBAL"))
            .getSql().replaceAll("\\s+", " ").trim();
        assertThat(sql.substring(7, sql.indexOf(" FROM ")).split(",\\s*"))
            .containsExactlyElementsOf(labels.keySet());
        assertThat(map.getResultMappings()).extracting(mapping -> mapping.getColumn())
            .containsExactlyElementsOf(labels.keySet());
        var row = new UserPreferenceRow();
        var meta = config.newMetaObject(row);
        for (var mapping : map.getResultMappings()) {
            assertThat(mapping.getJavaType()).isEqualTo(meta.getSetterType(mapping.getProperty()));
            assertThat(mapping.getTypeHandler()).isNotNull();
            meta.setValue(mapping.getProperty(), labels.get(mapping.getColumn()));
        }
        var result = UserPreferenceResponse.from("PAGE_SIZE", "GLOBAL", row).data();
        assertThat(result.prefVal()).isEqualTo("50");
        assertThat(result.modDttm()).isEqualTo("2026-02-03T04:05:06.123456Z");
    }
}
