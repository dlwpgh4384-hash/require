package com.company.common.schema;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

class MapperStartupParsingTest {
    @Test
    void allRuntimeMappersParseTogetherWithoutDatabase() throws Exception {
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.setAutoMappingUnknownColumnBehavior(
            org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
        var resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:mapper/**/*.xml");
        assertThat(resources).isNotEmpty();
        for (var resource : resources) {
            try (var input = resource.getInputStream()) {
                new XMLMapperBuilder(input, configuration, resource.toString(),
                    configuration.getSqlFragments()).parse();
            }
        }
        assertThat(configuration.getMappedStatementNames()).isNotEmpty();
        var detail = configuration.getResultMap(
            "com.company.common.partsettlements.mapper.PartSettlementMapper.detail");
        assertThat(detail.getIdResultMappings()).extracting(mapping -> mapping.getProperty())
            .containsExactly("developerId");
        assertThat(detail.getResultMappings()).extracting(mapping -> mapping.getProperty())
            .contains("partId", "developerId");
    }
}
