package com.company.common.mapper;

import com.company.common.dto.MenuItem;
import com.company.common.config.MyBatisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class MenuProjectionTest {
    @Test
    void actualXmlLabelsExplicitlyMapEveryNonDefaultValueWithoutDatabase() throws Exception {
        Configuration config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        try (var input = new ClassPathResource("mapper/MenuMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "mapper/MenuMapper.xml", config.getSqlFragments()).parse();
        }
        String statement = MenuMapper.class.getName() + ".selectCandidates";
        String sql = config.getMappedStatement(statement).getBoundSql(null).getSql().replaceAll("\\s+", " ").trim();
        assertThat(sql).endsWith("FROM TB_SY_MENU ORDER BY sort_ord, menu_id");
        assertThat(sql.toUpperCase()).doesNotContain("WHERE", "LOCK_YN", "REG_", "MOD_");
        var map = config.getMappedStatement(statement).getResultMaps().get(0);
        assertThat(map.getType()).isEqualTo(MenuRow.class);
        assertThat(map.getAutoMapping()).isFalse();
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("menu_id", "projection-menu");
        labels.put("upper_menu_id", "projection-parent");
        labels.put("menu_nm", "실제 매핑 이름");
        labels.put("scrn_id", "ADDED");
        labels.put("menu_lvl", 8);
        labels.put("sort_ord", 76543);
        labels.put("use_yn", "N");
        labels.put("pc_only_yn", "Y");
        labels.put("icon_nm", "calendar");
        String[] selected = sql.substring("SELECT ".length(), sql.indexOf(" FROM ")).split(",\\s*");
        assertThat(selected).containsExactlyElementsOf(labels.keySet());
        assertThat(map.getResultMappings()).extracting(mapping -> mapping.getColumn())
            .containsExactlyElementsOf(labels.keySet());
        MenuRow row = new MenuRow();
        var meta = config.newMetaObject(row);
        for (var mapping : map.getResultMappings()) {
            Object value = labels.get(mapping.getColumn());
            assertThat(meta.hasSetter(mapping.getProperty())).isTrue();
            assertThat(mapping.getTypeHandler()).isNotNull();
            assertThat(mapping.getJavaType()).isEqualTo(meta.getSetterType(mapping.getProperty()));
            meta.setValue(mapping.getProperty(), value);
            assertThat(meta.getValue(mapping.getProperty())).isEqualTo(value);
        }
        assertThat(MenuItem.from(row)).isEqualTo(new MenuItem("projection-menu", "projection-parent", "실제 매핑 이름", "ADDED", 8, 76543, "Y", "calendar"));
        assertThat(row.getUseYn()).isEqualTo("N");
        assertThat(new ObjectMapper().valueToTree(MenuItem.from(row)).size()).isEqualTo(8);
    }

    @Test
    void nullableFieldsAreExplicitAndNoInternalPropertyIsSerialized() throws Exception {
        var item = new MenuItem("id", null, "그룹", null, 1, 3, "N", null);
        var json = new ObjectMapper().valueToTree(item);
        assertThat(json.size()).isEqualTo(8);
        assertThat(json.get("upperMenuId").isNull()).isTrue();
        assertThat(json.get("scrnId").isNull()).isTrue();
        assertThat(json.get("iconNm").isNull()).isTrue();
        assertThat(json.has("useYn")).isFalse();
        assertThat(json.has("lockYn")).isFalse();
    }

    @Test
    void productionMapperScanIncludesTheRealMenuPackage() {
        MapperScan scan = MyBatisConfig.class.getAnnotation(MapperScan.class);
        assertThat(Arrays.asList(scan.basePackages())).contains(MenuMapper.class.getPackageName());
    }
}
