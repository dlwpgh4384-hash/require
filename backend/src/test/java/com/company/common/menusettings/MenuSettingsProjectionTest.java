package com.company.common.mapper;

import com.company.common.dto.MenuRegistrationContracts.Item;
import com.company.common.config.MyBatisConfig;
import com.company.common.mapper.MenuSettingsMapper;
import com.company.common.mapper.MenuSettingsRow;
import java.util.LinkedHashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class MenuSettingsProjectionTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void realXmlExplicitlyMaterializesAllFieldsWithNullsAndNonDefaults(boolean nullable) throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/MenuSettingsMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        var statement = config.getMappedStatement(MenuSettingsMapper.class.getName() + ".selectSettings");
        var mapping = statement.getResultMaps().get(0);
        var labels = new LinkedHashMap<String, Object>();
        labels.put("menu_id", "projection-menu"); labels.put("upper_menu_id", nullable ? null : "projection-parent");
        labels.put("menu_nm", "투영 메뉴"); labels.put("scrn_id", nullable ? null : "UI-25");
        labels.put("menu_lvl", 2); labels.put("sort_ord", 76543); labels.put("use_yn", "N");
        labels.put("pc_only_yn", "Y"); labels.put("lock_yn", "Y"); labels.put("icon_nm", nullable ? null : "gear");
        String sql = statement.getBoundSql(null).getSql().replaceAll("\\s+", " ").trim();
        assertThat(sql).doesNotContain("WHERE");
        assertThat(sql.substring(7, sql.indexOf(" FROM ")).split(",\\s*"))
            .containsExactlyElementsOf(labels.keySet());
        assertThat(mapping.getAutoMapping()).isFalse();
        assertThat(mapping.getType()).isEqualTo(MenuSettingsRow.class);
        assertThat(mapping.getResultMappings()).extracting(row -> row.getColumn()).containsExactlyElementsOf(labels.keySet());
        var row = new MenuSettingsRow();
        var meta = config.newMetaObject(row);
        for (var field : mapping.getResultMappings()) {
            assertThat(field.getJavaType()).isEqualTo(meta.getSetterType(field.getProperty()));
            assertThat(field.getTypeHandler()).isNotNull();
            meta.setValue(field.getProperty(), labels.get(field.getColumn()));
        }
        assertThat(row.value()).isEqualTo(new Item("projection-menu", nullable ? null : "projection-parent",
            "투영 메뉴", nullable ? null : "UI-25", 2, 76543, "N", "Y", "Y", nullable ? null : "gear"));
    }
    @Test void productionConfigurationScansOnlyOwnedAnnotatedMappers() {
        var scan = MyBatisConfig.class.getAnnotation(MapperScan.class);
        assertThat(scan.basePackages()).contains(MenuSettingsMapper.class.getPackageName());
        assertThat(scan.annotationClass()).isEqualTo(org.apache.ibatis.annotations.Mapper.class);
    }
}
