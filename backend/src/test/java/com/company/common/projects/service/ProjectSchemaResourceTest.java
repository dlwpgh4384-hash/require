package com.company.common.projects.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ProjectSchemaResourceTest {
    @Test
    void provisionsOriginalProjectKeysAndNeverSeedsBusinessOrFormulaRows() throws Exception {
        String sql = new ClassPathResource("db/migration/V12__project_operations_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        for (String table : List.of("TB_PJ_PROJ_CHG", "TB_PJ_MNPW_PLAN", "TB_PJ_TK_DSTRB",
                "TB_PJ_DRCT_EXPS", "TB_PJ_ASGN_HIST", "TB_PJ_ASGN_MON", "TB_CO_MON_COST",
                "TB_PJ_STEP", "TB_PJ_ISSUE", "TB_PJ_WEEK_MEMO")) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table, "COMMENT ON TABLE " + table);
        }
        assertThat(sql).contains("PRIMARY KEY (PROJ_ID, EXPS_YM, EXPS_SN)",
            "PRIMARY KEY (PROJ_ID, DSTRB_STEP_CD, ROW_SN, DSTRB_YM)",
            "PRIMARY KEY (COST_YM, DEV_ID, PROJ_ID, ASGN_ID)",
            "CHG_MEMO VARCHAR(200)", "PLAN_MM NUMERIC(6,2) NOT NULL",
            "NEW_CTRT_MM NUMERIC(4,2)", "ERRCODE='P0001'");
        assertThat(sql).doesNotContain("INSERT INTO", "UPDATE TB_", "DELETE FROM", "EXPS_ID",
            "TB_CO_MON_PL", "TB_SL_OPP", "ALTER COLUMN");
    }
}
