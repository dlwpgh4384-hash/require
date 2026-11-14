package com.company.common.mapper;

import com.company.common.dto.CodeHistoryContracts.Item;
import com.company.common.mapper.CodeHistoryRow;
import com.company.common.mapper.CommonCodeHistoryMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class CommonCodeHistoryProjectionTest {
    @Test void realXmlExplicitlyMaterializesEachNonDefaultField() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/CommonCodeHistoryMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        var statement = config.getMappedStatement(CommonCodeHistoryMapper.class.getName() + ".selectChanges");
        var mapping = statement.getResultMaps().get(0);
        var time = Instant.parse("2026-11-12T15:34:56.123456Z");
        var labels = new LinkedHashMap<String, Object>();
        labels.put("reg_dttm", time);
        labels.put("kind", "운영 기준값");
        labels.put("item", "STD_VAL");
        labels.put("before_value", "1500000.1234");
        labels.put("after_value", "2500000.5678");
        labels.put("reg_user_id", "admin");
        assertThat(mapping.getAutoMapping()).isFalse();
        assertThat(mapping.getType()).isEqualTo(CodeHistoryRow.class);
        assertThat(mapping.getResultMappings()).extracting(field -> field.getColumn())
            .containsExactlyElementsOf(labels.keySet());
        var row = new CodeHistoryRow();
        var meta = config.newMetaObject(row);
        for (var field : mapping.getResultMappings()) {
            assertThat(field.getJavaType()).isEqualTo(meta.getSetterType(field.getProperty()));
            assertThat(field.getTypeHandler()).isNotNull();
            meta.setValue(field.getProperty(), labels.get(field.getColumn()));
        }
        assertThat(row.value()).isEqualTo(new Item(time, "운영 기준값", "STD_VAL",
            "1500000.1234", "2500000.5678", "admin"));
        String sql = statement.getBoundSql(null).getSql().replaceAll("\\s+", " ").trim();
        assertThat(sql).contains("FROM TB_SY_CHG_HIST h",
            "WHERE h.trgt_tbl_nm IN ('TB_SY_CODE_GRP', 'TB_SY_CODE', 'TB_SY_OPER_STD', 'TB_SY_CODE_MAP')",
            "ORDER BY h.reg_dttm DESC, h.hist_id DESC",
            "COALESCE(h.chg_item_nm, h.trgt_key) AS item",
            "COALESCE(h.bfr_val, '') AS before_value", "COALESCE(h.aft_val, '') AS after_value");
        assertThat(sql).doesNotContain("LIMIT", "OFFSET", "JOIN", "TB_SY_USER", "chg_type_cd=");
        assertThat(statement.getBoundSql(null).getParameterMappings()).isEmpty();
    }
}
