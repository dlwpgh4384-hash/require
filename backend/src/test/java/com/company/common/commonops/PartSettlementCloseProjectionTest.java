package com.company.common.commonops;

import com.company.common.commonops.dto.PartSettlementCloseContracts.Period;
import com.company.common.commonops.mapper.PartSettlementCloseMapper;
import com.company.common.commonops.mapper.PartSettlementCloseRow;
import com.company.common.commonops.service.PartSettlementCloseService;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class PartSettlementCloseProjectionTest {
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/PartSettlementCloseMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }

    @Test void selectedLabelsMaterializeEveryFieldWithTheProductionResultMap() throws Exception {
        var config = configuration();
        var statement = config.getMappedStatement(PartSettlementCloseMapper.class.getName() + ".selectTargets");
        var mapping = statement.getResultMaps().get(0);
        var labels = new LinkedHashMap<String, Object>();
        labels.put("part_id", "mapped-part");
        labels.put("confirmed", true);
        labels.put("mod_dttm", Instant.parse("2026-11-01T02:03:04.123456Z"));
        assertThat(mapping.getAutoMapping()).isFalse();
        assertThat(mapping.getResultMappings()).extracting(field -> field.getColumn())
            .containsExactlyElementsOf(labels.keySet());
        var row = (PartSettlementCloseRow) mapping.getType().getDeclaredConstructor().newInstance();
        var meta = config.newMetaObject(row);
        for (var field : mapping.getResultMappings()) {
            assertThat(field.getJavaType()).isEqualTo(meta.getSetterType(field.getProperty()));
            assertThat(field.getTypeHandler()).isNotNull();
            meta.setValue(field.getProperty(), labels.get(field.getColumn()));
        }
        assertThat(row.partId).isEqualTo("mapped-part");
        assertThat(row.confirmed).isTrue();
        assertThat(row.modifiedAt).isEqualTo(labels.get("mod_dttm"));
        meta.setValue("modifiedAt", null);
        meta.setValue("confirmed", false);
        assertThat(row.modifiedAt).isNull();
        assertThat(row.confirmed).isFalse();
        var sql = statement.getBoundSql(new Period("202610", Instant.parse("2026-10-31T15:00:00Z")));
        for (var parameter : sql.getParameterMappings()) {
            assertThat(config.newMetaObject(sql.getParameterObject()).hasGetter(parameter.getProperty())).isTrue();
        }
    }

    @Test void sharedCheckBoundaryIsReadOnlyRepeatableRead() throws Exception {
        var boundary = PartSettlementCloseService.class.getMethod("check", String.class)
            .getAnnotation(Transactional.class);
        assertThat(boundary.readOnly()).isTrue();
        assertThat(boundary.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
