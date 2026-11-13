package com.company.common.commonops;

import com.company.common.mapper.MenuRegistrationMapper;
import com.company.common.mapper.MenuRegistrationRow;
import com.company.common.service.DevelopedScreenRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class MenuRegistrationProjectionTest {
    @Test void explicitProductionMappingCoversEverySelectedLabelAndNullableField() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        try (var input = new ClassPathResource("mapper/MenuRegistrationMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "mapper/MenuRegistrationMapper.xml", config.getSqlFragments()).parse();
        }
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("menu_id", "G009"); labels.put("upper_menu_id", null);
        labels.put("menu_nm", "투영 이름"); labels.put("scrn_id", null);
        labels.put("menu_lvl", 1); labels.put("sort_ord", 76543);
        labels.put("use_yn", "N"); labels.put("pc_only_yn", "Y");
        labels.put("lock_yn", "Y"); labels.put("icon_nm", "folder");
        for (String operation : new String[]{"selectParents", "selectMenu"}) {
            var statement = config.getMappedStatement(MenuRegistrationMapper.class.getName() + "." + operation);
            String sql = statement.getBoundSql(Map.of("id", "G009")).getSql().replaceAll("\\s+", " ").trim();
            assertThat(sql.substring(7, sql.indexOf(" FROM ")).split(",\\s*"))
                .containsExactlyElementsOf(labels.keySet());
            var map = statement.getResultMaps().get(0);
            assertThat(map.getAutoMapping()).isFalse();
            assertThat(map.getResultMappings()).extracting(row -> row.getColumn())
                .containsExactlyElementsOf(labels.keySet());
            var row = new MenuRegistrationRow();
            var meta = config.newMetaObject(row);
            for (var mapping : map.getResultMappings()) {
                assertThat(mapping.getJavaType()).isEqualTo(meta.getSetterType(mapping.getProperty()));
                assertThat(mapping.getTypeHandler()).isNotNull();
                meta.setValue(mapping.getProperty(), labels.get(mapping.getColumn()));
                assertThat(meta.getValue(mapping.getProperty())).isEqualTo(labels.get(mapping.getColumn()));
            }
            var json = new ObjectMapper().valueToTree(row.value());
            assertThat(json.size()).isEqualTo(10);
            assertThat(json.get("upperMenuId")).isEqualTo(new ObjectMapper().valueToTree(labels.get("upper_menu_id")));
            assertThat(json.get("scrnId")).isEqualTo(new ObjectMapper().valueToTree(labels.get("scrn_id")));
            assertThat(json.get("iconNm")).isEqualTo(new ObjectMapper().valueToTree(labels.get("icon_nm")));
            assertThat(json.get("lockYn").asText()).isEqualTo("Y");
            labels.put("upper_menu_id", "G007"); labels.put("scrn_id", "UI-25");
            labels.put("menu_lvl", 2); labels.put("icon_nm", null);
        }
    }
    @Test void registryContainsOnlyImplementedScreenContracts() {
        assertThat(new DevelopedScreenRegistry().screens()).extracting(row -> row.scrnId())
            .containsExactly("UI-23", "UI-24", "UI-25");
        assertThat(new DevelopedScreenRegistry().contains("UI-27")).isFalse();
    }
}
