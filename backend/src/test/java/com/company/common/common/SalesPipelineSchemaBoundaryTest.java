package com.company.common.common;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class SalesPipelineSchemaBoundaryTest {
    private String migration() throws Exception {
        return new ClassPathResource("db/migration/V21__sales_pipeline_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void onlyOriginalApprovalHistoryColumnsAndCompositeKeyAreDefined() throws Exception {
        String sql = migration();
        assertThat(Pattern.compile("CREATE TABLE IF NOT EXISTS (TB_[A-Z_]+) ").matcher(sql)
            .results().map(match -> match.group(1)).toList()).containsExactly("TB_SL_APRV_HIST");
        assertThat(sql).contains("OPP_ID VARCHAR(20) NOT NULL", "HIST_SN NUMERIC(5) NOT NULL",
            "APRV_ACT_CD VARCHAR(10) NOT NULL", "PRFT_RT NUMERIC(7,2) NOT NULL", "ACT_RSN VARCHAR(300)",
            "REG_USER_ID VARCHAR(30) NOT NULL", "REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL",
            "CONSTRAINT PK_TB_SL_APRV_HIST PRIMARY KEY (OPP_ID, HIST_SN)");
        assertThat(sql).doesNotContain("MOD_DTTM", "MOD_USER_ID", "CREATE SEQUENCE");
    }

    @Test
    void foreignKeyIsAddedOnlyOnCreationAndExcludedFromExistingShapeComparison() throws Exception {
        String sql = migration();
        assertThat(Pattern.compile("CONSTRAINT (FK_TB_[A-Z0-9_]+)").matcher(sql)
            .results().map(match -> match.group(1)).toList()).containsExactly("FK_TB_SL_APRV_HIST_01");
        String creation = sql.substring(sql.indexOf("IF relation IS NULL THEN"), sql.indexOf("END IF;"));
        assertThat(creation).contains("CONSTRAINT FK_TB_SL_APRV_HIST_01 FOREIGN KEY (OPP_ID) REFERENCES TB_SL_OPP (OPP_ID)");
        assertThat(sql).contains("c.contype IN ('p', 'c', 'u')", "actual_columns IS DISTINCT FROM expected_columns",
            "actual_constraints IS DISTINCT FROM expected_constraints", "ERRCODE='P0001'");
        assertThat(sql).doesNotContain("c.contype = 'f'", "c.contype='f'", "ALTER TABLE", "ON DELETE", "ON UPDATE");
    }

    @Test
    void preparationNeverBackfillsOrChangesExistingBusinessData() throws Exception {
        assertThat(migration()).doesNotContain("INSERT INTO", "UPDATE TB_", "DELETE FROM", "TRUNCATE",
            "TB_SL_OPP_NEED", "TB_SL_TENT_ASGN", "TB_SY_CODE", "TB_SY_CALC_RULE");
        assertThat(migration()).contains("pg_advisory_xact_lock(734901, 1)",
            "COMMENT ON TABLE TB_SL_APRV_HIST", "COMMENT ON COLUMN TB_SL_APRV_HIST.REG_USER_ID",
            "REQ:요청|OK:승인|REJ:반려|AUTO_REL:요구 해제");
    }
}
