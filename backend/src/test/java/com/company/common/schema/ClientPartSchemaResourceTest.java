package com.company.common.schema;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ClientPartSchemaResourceTest {
    @Test void packagedMigrationContainsOnlyApprovedTablesAndNoSeedWrites() throws Exception {
        String sql = new ClassPathResource("db/migration/V10__client_part_upload_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        var tables = java.util.regex.Pattern.compile("CREATE TABLE IF NOT EXISTS (TB_[A-Z_]+)")
            .matcher(sql).results().map(row -> row.group(1)).toList();
        assertThat(tables).containsExactly("TB_BS_CLNT", "TB_SY_UPLD", "TB_SY_UPLD_ERR", "TB_SY_ADM_AREA",
            "TB_HR_DEV", "TB_PJ_ASGN", "TB_PJ_PROJ_PART", "TB_CO_PART_SETL");
        assertThat(sql).doesNotContain("INSERT INTO", "DELETE FROM", "TRUNCATE", "DROP TABLE", "ALTER COLUMN",
            "ALTER TABLE TB_BS_PART ", "ALTER TABLE TB_BS_PART_STD ", "ALTER TABLE TB_PJ_PROJ ");
        assertThat(sql).contains("CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_UPLD START WITH 1 INCREMENT BY 1",
            "ERRCODE='P0001'", "pg_advisory_xact_lock(734901, 1)");
        assertThat(java.util.regex.Pattern.compile("'ALTER TABLE [A-Z_]+ ADD CONSTRAINT [A-Z_0-9]+ FOREIGN KEY")
            .matcher(sql).results().count()).isEqualTo(12);
        assertThat(java.util.regex.Pattern.compile("'CREATE INDEX IF NOT EXISTS IX_[A-Z_0-9]+ ON")
            .matcher(sql).results().count()).isEqualTo(15);
    }
}
