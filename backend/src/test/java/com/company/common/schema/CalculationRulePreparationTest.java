package com.company.common.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class CalculationRulePreparationTest {
    private String resource(String name) throws Exception {
        return new ClassPathResource("db/migration/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
    @Test void seedContainsAll94OriginalTuplesFromTheDml() throws Exception {
        String seed = resource("V8__calculation_rule_seed.sql");
        var matcher = Pattern.compile("^\\('식-([0-9]{2})',", Pattern.MULTILINE).matcher(seed);
        var ids = new java.util.ArrayList<Integer>();
        while (matcher.find()) ids.add(Integer.parseInt(matcher.group(1)));
        // DML 원문: 식-00..식-96 중 식-03·65·78은 원래 없음 → 94줄(2026-11-14 바로잡음).
        assertThat(ids).hasSize(94).doesNotHaveDuplicates();
        assertThat(ids).containsExactlyElementsOf(IntStream.rangeClosed(0, 96)
            .filter(id -> !List.of(3, 65, 78).contains(id)).boxed().toList());
        assertThat(seed).contains("IF NOT EXISTS (SELECT 1 FROM TB_SY_CALC_RULE)",
            "LOCK TABLE TB_SY_CALC_RULE IN EXCLUSIVE MODE");
        assertThat(seed).doesNotContain("UPDATE ", "DELETE ", "ON CONFLICT", "TRUNCATE", "COMMIT;");
    }
    @Test void fourRequiredDefinitionsKeepOriginalFormulaAndDetailsInPackagedSql() throws Exception {
        String seed = resource("V8__calculation_rule_seed.sql");
        try (var input = new ClassPathResource("calculation/source-definitions.json").getInputStream()) {
            var definitions = new ObjectMapper().readTree(input);
            assertThat(definitions.size()).isEqualTo(4);
            for (var definition : definitions) {
                assertThat(seed).contains("'" + definition.get("formula").asText().replace("'", "''") + "'");
                assertThat(seed).contains("'" + definition.get("details").asText().replace("'", "''") + "'");
            }
        }
    }
    @Test void preparationAddsOnlyOriginalDefinitionAndMappingTablesWithoutForeignKeysOrDestructiveChanges() throws Exception {
        var ddl = resource("V7__code_definitions_schema.sql");
        var tables = Pattern.compile("CREATE TABLE IF NOT EXISTS (TB_[A-Z_]+)").matcher(ddl)
            .results().map(row -> row.group(1)).toList();
        assertThat(tables).containsExactly("TB_SY_CODE_MAP", "TB_SY_CALC_RULE");
        assertThat(ddl).contains("UNIQUE (SRC_TEXT, CODE_GRP_ID)", "PRIMARY KEY (MAP_ID)",
            "PRIMARY KEY (CALC_ID)", "FRML_CN TEXT NOT NULL", "DTL_CN TEXT NOT NULL",
            "RAISE EXCEPTION", "ERRCODE='P0001'", "COMMENT ON TABLE TB_SY_CODE_MAP", "COMMENT ON TABLE TB_SY_CALC_RULE");
        assertThat(ddl).doesNotContain("ALTER TABLE", "DROP TABLE", "FOREIGN KEY", "INSERT INTO TB_SY_CODE_MAP");
    }
}
