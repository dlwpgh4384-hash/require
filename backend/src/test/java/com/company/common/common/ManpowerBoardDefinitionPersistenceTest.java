package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.support.service.CodeFailure;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ManpowerBoardDefinitionPersistenceTest {
    private ManpowerBoardFixture fixture;

    @BeforeEach
    void independentSchema() throws Exception {
        fixture = new ManpowerBoardFixture();
    }

    @AfterEach
    void removeOwnedSchema() throws Exception {
        if (fixture != null) fixture.close();
    }

    @Test
    void seedBackedDefinitionsAreReadThroughRealMapperWithoutBusinessWrites() {
        fixture.boardSources();
        String before = fixture.businessSnapshot();
        try (var session = fixture.factory().openSession()) {
            var definitions = new ManpowerBoardDefinitions(fixture.metadata(session)).definitions();
            assertThat(definitions).extracting(Definition::calcId).containsExactly(
                "식-00", "식-01", "식-60", "식-61", "식-62", "식-63", "식-77", "식-95");
            for (var definition : definitions) {
                var row = fixture.db().queryForMap(
                    "SELECT FRML_CN,DTL_CN FROM TB_SY_CALC_RULE WHERE CALC_ID=?", definition.calcId());
                assertThat(definition.formula()).isEqualTo(row.get("frml_cn"));
                assertThat(definition.details()).isEqualTo(row.get("dtl_cn"));
                assertThat(definition.formula()).isNotBlank();
                assertThat(definition.details()).isNotBlank();
            }
        }
        assertThat(fixture.businessSnapshot()).isEqualTo(before);
        assertThat(fixture.counter().calls()).hasSize(1).containsValue(8);
    }

    @Test
    void privateSchemaDefinitionChangeIsSeenByANewRequestWithoutRestoringIt() {
        fixture.db().update("UPDATE TB_SY_CALC_RULE SET FRML_CN=?,DTL_CN=? WHERE CALC_ID=?",
            "독립 시험에서 바꾼 식", "독립 시험에서 바꾼 상세", "식-63");
        String before = fixture.businessSnapshot();
        try (var session = fixture.factory().openSession()) {
            var values = new ManpowerBoardDefinitions(fixture.metadata(session)).definitions();
            assertThat(values).contains(new Definition("식-63", "독립 시험에서 바꾼 식", "독립 시험에서 바꾼 상세"));
        }
        assertThat(fixture.businessSnapshot()).isEqualTo(before);
    }

    @Test
    void missingDefinitionFailsWithoutInsertingReplacement() {
        fixture.db().update("DELETE FROM TB_SY_CALC_RULE WHERE CALC_ID=?", "식-63");
        String before = fixture.businessSnapshot();
        try (var session = fixture.factory().openSession()) {
            var definitions = new ManpowerBoardDefinitions(fixture.metadata(session));
            assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, failure -> {
                assertThat(failure.getStatus()).isEqualTo(400);
                assertThat(failure.getField()).isEqualTo("calcId");
            });
        }
        assertThat(fixture.businessSnapshot()).isEqualTo(before);
    }

    @Test
    void boardFixturesOwnUniqueKeysAndRetainRawAssignmentAndTentativeSources() {
        var first = fixture.boardSources();
        var second = fixture.boardSources();
        assertThat(first.values()).doesNotContainAnyElementsOf(second.values());
        assertThat(fixture.db().queryForObject("SELECT USE_YN FROM TB_HR_DEV WHERE DEV_ID=?",
            String.class, first.get("retired"))).isEqualTo("N");
        assertThat(fixture.db().queryForObject("""
            SELECT SUM(MON_MM) FROM TB_PJ_ASGN_MON WHERE DEV_ID=? AND INPUT_YM='202611'
            """, java.math.BigDecimal.class, first.get("excess"))).isEqualByComparingTo("1.30");
        assertThat(fixture.db().queryForObject("""
            SELECT COUNT(*) FROM TB_PJ_ASGN WHERE DEV_ID=? AND ASGN_STAT_CD='ACT'
            """, Integer.class, first.get("concurrent"))).isEqualTo(2);
        assertThat(fixture.db().queryForList("SELECT DISTINCT TENT_STAT_CD FROM TB_SL_TENT_ASGN",
            String.class)).containsExactlyInAnyOrderElementsOf(List.of("ACT", "CNCL", "CNVT"));
        assertThat(fixture.db().queryForObject("""
            SELECT COUNT(*) FROM TB_SL_TENT_ASGN t JOIN TB_SL_OPP o ON o.OPP_ID=t.OPP_ID
            WHERE t.DEV_ID=? AND t.TENT_STAT_CD='ACT' AND o.OPP_STAT_CD='ING'
            """, Integer.class, first.get("idle"))).isZero();
    }
}
