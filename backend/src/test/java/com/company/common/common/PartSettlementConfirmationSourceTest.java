package com.company.common.common;

import com.company.common.parts.mapper.PartMapper;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class PartSettlementConfirmationSourceTest {
    @Test
    void existingCriteriaQueryIncludesAutomaticConfirmationAndRetainsItsScopeAndCloseExclusion() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/PartMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        var statement = config.getMappedStatement(PartMapper.class.getName() + ".selectLastConfirmed");
        var query = statement.getBoundSql(Map.of("id", "owned-part", "month", "202611"));
        assertThat(query.getSql().replaceAll("\\s+", " "))
            .contains("SELECT max(s.setl_ym)", "s.part_id=?", "s.setl_ym >= ?",
                "s.setl_stat_cd IN ('CNFM','AUTO')", "NOT EXISTS", "c.close_ym=s.setl_ym",
                "c.close_stat_cd='CLOSE'")
            .doesNotContain("owned-part", "202611");
        assertThat(query.getParameterMappings()).extracting(mapping -> mapping.getProperty())
            .containsExactly("id", "month");
        assertThat(statement.getResultMaps()).singleElement().satisfies(mapping ->
            assertThat(mapping.getType()).isEqualTo(String.class));
    }
}
