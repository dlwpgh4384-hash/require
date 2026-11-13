package com.company.common.mapper;

import com.company.common.dto.CodeMapContracts.Item;
import java.util.LinkedHashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class CommonCodeMapProjectionTest {
    @Test void actualXmlMaterializesPhysicalColumnsAndHidesInternalIdentity() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/CommonCodeMapMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        var labels = new LinkedHashMap<String, Object>();
        labels.put("map_id", 9876543210L);
        labels.put("src_text", "원문 표기");
        labels.put("code_grp_id", "EMP_TYPE");
        labels.put("code", "FULL");
        for (String operation : java.util.List.of("selectMaps", "selectIdentity")) {
            var statement = config.getMappedStatement(CommonCodeMapMapper.class.getName() + "." + operation);
            var mapping = statement.getResultMaps().get(0);
            var sql = statement.getBoundSql(new Item("원문 표기", "EMP_TYPE", "FULL"))
                .getSql().replaceAll("\\s+", " ").trim();
            assertThat(sql.substring(7, sql.indexOf(" FROM ")).split(",\\s*"))
                .containsExactlyElementsOf(labels.keySet());
            assertThat(mapping.getAutoMapping()).isFalse();
            assertThat(mapping.getType()).isEqualTo(CodeMapRow.class);
            assertThat(mapping.getResultMappings()).extracting(field -> field.getColumn())
                .containsExactlyElementsOf(labels.keySet());
            var row = new CodeMapRow();
            var meta = config.newMetaObject(row);
            for (var field : mapping.getResultMappings()) {
                assertThat(field.getJavaType()).isEqualTo(meta.getSetterType(field.getProperty()));
                assertThat(field.getTypeHandler()).isNotNull();
                meta.setValue(field.getProperty(), labels.get(field.getColumn()));
            }
            assertThat(row.mapId).isEqualTo(9876543210L);
            assertThat(row.value()).isEqualTo(new Item("원문 표기", "EMP_TYPE", "FULL"));
        }
        var insert = config.getMappedStatement(CommonCodeMapMapper.class.getName() + ".insertMap");
        assertThat(insert.getKeyProperties()).containsExactly("mapId");
        assertThat(insert.getKeyColumns()).containsExactly("map_id");
        assertThat(insert.getBoundSql(new CodeMapRow()).getSql()).contains("'admin'", "clock_timestamp()");
        var delete = config.getMappedStatement(CommonCodeMapMapper.class.getName() + ".deleteMap");
        assertThat(delete.getBoundSql(java.util.Map.of("mapId", 9L)).getSql())
            .contains("DELETE FROM TB_SY_CODE_MAP").doesNotContain("DEL_YN");
    }
}
