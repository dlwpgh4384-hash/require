package com.company.common.common;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class CandidateSchemaBoundaryTest {
    private String migration() throws Exception {
        return new ClassPathResource("db/migration/V20__candidate_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void onlyTheThreeOriginalCandidateTablesAreDefined() throws Exception {
        var tables = Pattern.compile("CREATE TABLE IF NOT EXISTS (TB_[A-Z_]+) ")
            .matcher(migration()).results().map(match -> match.group(1)).toList();
        assertThat(tables).containsExactly("TB_HR_CAND", "TB_HR_CAND_SKILL", "TB_HR_CAND_CNTC");
        assertThat(migration()).contains("CAND_ID VARCHAR(20) NOT NULL", "HOPE_UPRC NUMERIC(15) NOT NULL",
            "INPUT_PSBL_DT DATE NOT NULL", "CNTC_SN NUMERIC(5) NOT NULL", "CNTC_MEMO VARCHAR(200) NOT NULL",
            "CONSTRAINT PK_TB_HR_CAND PRIMARY KEY (CAND_ID)",
            "CONSTRAINT PK_TB_HR_CAND_SKILL PRIMARY KEY (CAND_ID, SKILL_NM)",
            "CONSTRAINT PK_TB_HR_CAND_CNTC PRIMARY KEY (CAND_ID, CNTC_SN)",
            "CONSTRAINT CK_TB_HR_CAND_01 CHECK (PRVC_AGRE_YN IN ('Y', 'N'))");
    }

    @Test
    void creationContainsOnlyOriginalForeignKeyTargetsWithoutExistingForeignKeyEnforcement() throws Exception {
        String sql = migration();
        assertThat(Pattern.compile("CONSTRAINT (FK_TB_[A-Z0-9_]+)").matcher(sql)
            .results().map(match -> match.group(1)).toList())
            .containsExactly("FK_TB_HR_CAND_01", "FK_TB_HR_CAND_02",
                "FK_TB_HR_CAND_SKILL_01", "FK_TB_HR_CAND_CNTC_01");
        assertThat(sql).contains("FOREIGN KEY (BLNG_CLNT_ID) REFERENCES TB_BS_CLNT (CLNT_ID)",
            "FOREIGN KEY (HOME_AREA_CD) REFERENCES TB_SY_ADM_AREA (AREA_CD)",
            "FOREIGN KEY (CAND_ID) REFERENCES TB_HR_CAND (CAND_ID)",
            "IF relation IS NULL THEN", "c.contype IN ('p', 'c', 'u')");
        assertThat(sql).doesNotContain("c.contype = 'f'", "c.contype='f'", "ALTER TABLE", "ON DELETE", "ON UPDATE");
    }

    @Test
    void preparationNeverSeedsCandidatesCodesOrCalculationsOrAddsResumeStorage() throws Exception {
        String sql = migration();
        assertThat(sql).doesNotContain("INSERT INTO", "UPDATE TB_", "DELETE FROM", "TRUNCATE",
            "CREATE SEQUENCE", "TB_HR_DEV", "TB_SY_UPLD", "TB_SY_CHG_HIST", "TB_SY_CALC_RULE", "TB_SY_CODE",
            "RESUME_TEXT", "SYSTEM_RECORD", "UPLOAD_ID");
        assertThat(sql).contains("pg_advisory_xact_lock(734901, 1)",
            "actual_columns IS DISTINCT FROM expected_columns",
            "actual_constraints IS DISTINCT FROM expected_constraints", "ERRCODE='P0001'");
    }
}
