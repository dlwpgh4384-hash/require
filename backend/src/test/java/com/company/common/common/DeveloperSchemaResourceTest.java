package com.company.common.common;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class DeveloperSchemaResourceTest {
    private String sql() throws Exception {
        return new ClassPathResource("db/migration/V11__developer_pool_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test void onlyFiveOriginalTablesAreCreated() throws Exception {
        String sql = sql();
        assertThat(Pattern.compile("CREATE TABLE IF NOT EXISTS (TB_[A-Z_]+)")
            .matcher(sql).results().map(row -> row.group(1)).toList())
            .containsExactly("TB_HR_DEV_PRC", "TB_HR_DEV_SKILL", "TB_HR_DEV_STAT", "TB_HR_MON_PAY", "TB_SY_ADM_HDONG");
        assertThat(sql).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM", "ALTER COLUMN", "CREATE SEQUENCE");
        for (String table : new String[]{"TB_HR_DEV", "TB_PJ_ASGN", "TB_SY_ADM_AREA", "TB_BS_CLNT", "TB_BS_PART"}) {
            assertThat(sql).doesNotContain("ALTER TABLE " + table + " ", "CREATE TABLE IF NOT EXISTS " + table + " (");
        }
        assertThat(sql).contains("ERRCODE='P0001'", "pg_advisory_xact_lock(734901, 1)");
    }

    @Test void keepsPhysicalSkillHistorySnapshotAndReversalColumns() throws Exception {
        assertThat(sql()).contains("SKILL_NM VARCHAR(50)", "SORT_ORD NUMERIC(3)",
            "CLOSE_LEAVE_SN NUMERIC(5)", "ORGNL_LEAVE_END_DT DATE",
            "EMP_TYPE_CD VARCHAR(10) NOT NULL", "DRCT_CLS_CD VARCHAR(10) NOT NULL",
            "PRIMARY KEY (DEV_ID, APLY_BGN_YM)", "PRIMARY KEY (PAY_YM, DEV_ID)",
            "PRIMARY KEY (HDONG_CD, AREA_CD)")
            .doesNotContain("SKILL_CD", "CANCEL_YN", "EXPENSE", "EXPS_AMT");
    }

    @Test void eachAddressTableIsSeededOnlyWhenItIsEmpty() throws Exception {
        String sql = sql();
        assertThat(sql).contains("IF NOT EXISTS (SELECT 1 FROM TB_SY_ADM_AREA) THEN",
            "IF NOT EXISTS (SELECT 1 FROM TB_SY_ADM_HDONG) THEN",
            "LOCK TABLE TB_SY_ADM_AREA, TB_SY_ADM_HDONG IN EXCLUSIVE MODE");
        assertThat(Pattern.compile("INSERT INTO (TB_[A-Z_]+)").matcher(sql)
            .results().map(row -> row.group(1)).distinct().toList())
            .containsExactly("TB_SY_ADM_AREA", "TB_SY_ADM_HDONG");
        assertThat(sql).doesNotContain("INSERT INTO TB_SY_CODE", "INSERT INTO TB_SY_CALC_RULE", "ON CONFLICT");
    }
}
