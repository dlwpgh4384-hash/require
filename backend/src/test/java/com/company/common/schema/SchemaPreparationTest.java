package com.company.common.schema;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchemaPreparationTest {
    private final SqlSessionFactory factory = mock(SqlSessionFactory.class);
    private final SqlSession session = mock(SqlSession.class);
    private final Connection connection = mock(Connection.class);
    private final Statement statement = mock(Statement.class);
    private final SchemaPreparation preparation = new SchemaPreparation(factory);

    /** Migration scripts packaged under db/migration (V{n}__*.sql), read at test time so a new migration needs no count edit here. */
    private static java.util.List<String> migrationScripts() throws Exception {
        var resources = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
            .getResources("classpath*:db/migration/V*__*.sql");
        var scripts = new java.util.ArrayList<String>();
        for (var resource : resources) scripts.add(resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(scripts).isNotEmpty();
        return scripts;
    }

    private static int migrationCount() throws Exception {
        return migrationScripts().size();
    }

    private static int tableDefinitionCount(java.util.regex.Pattern tablePattern) throws Exception {
        int count = 0;
        for (String sql : migrationScripts()) count += (int) tablePattern.matcher(sql).results().count();
        return count;
    }

    @BeforeEach
    void connect() throws Exception {
        when(factory.openSession(false)).thenReturn(session);
        when(session.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
    }

    @Test
    void allPackagedResourcesExecuteBeforeSingleCommit() throws Exception {
        preparation.prepare();
        var ordered = inOrder(factory, session, connection, statement);
        ordered.verify(factory).openSession(false);
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_NOTI"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("INSERT INTO TB_SY_MENU"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("INSERT INTO TB_SY_OPER_STD"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("INSERT INTO TB_SY_USER_ROLE"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS TB_SY_ROLE_MENU_AUTH"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("CREATE INDEX IF NOT EXISTS IX_TB_SY_ROLE_MENU_AUTH_01"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS TB_SY_CODE_MAP"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("INSERT INTO TB_SY_CALC_RULE"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("DO $common_entity_contract$"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("DO $client_part_upload$"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("DO $developer_pool$"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("DO $project_schema$"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("DO $assignment_sources$"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("DO $recommendation_log$"));
        ordered.verify(statement).close();
        ordered.verify(session).getConnection();
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute(contains("DO $billing_receipt$"));
        ordered.verify(statement).close();
        ordered.verify(session).commit(true);
        ordered.verify(session).close();
        verify(session, never()).rollback(anyBoolean());
    }

    @Test
    void schemaRefusalRollsBackAndDoesNotReachSeed() throws Exception {
        when(statement.execute(anyString())).thenThrow(new SQLException("table.column expected numeric(5,0) actual text", "P0001"));
        assertThatThrownBy(preparation::prepare).isInstanceOf(SQLException.class);
        verify(statement, times(1)).execute(anyString());
        verify(session).rollback(true);
        verify(session, never()).commit(anyBoolean());
        verify(session).close();
    }

    @Test
    void failedSeedRollsBackSchemaAndSeedTogether() throws Exception {
        when(statement.execute(anyString())).thenReturn(true).thenThrow(new SQLException("injected failure"));
        assertThatThrownBy(preparation::prepare).isInstanceOf(SQLException.class);
        verify(session).rollback(true);
        verify(session, never()).commit(anyBoolean());
    }

    @Test
    void migrationResourcesContainOnlyModelTablesAndBaseSeeds() throws Exception {
        preparation.prepare();
        var resources = ArgumentCaptor.forClass(String.class);
        verify(statement, times(migrationCount())).execute(resources.capture());
        String schema = resources.getAllValues().get(0);
        var tables = java.util.regex.Pattern.compile("CREATE TABLE IF NOT EXISTS (TB_[A-Z_]+)").matcher(schema);
        var names = new java.util.ArrayList<String>();
        while (tables.find()) names.add(tables.group(1));
        assertThat(names).containsExactly("TB_SY_USER_SCOPE", "TB_SY_MENU", "TB_SY_USER_PREF", "TB_SY_NOTI", "TB_BS_PART_STD", "TB_PJ_PROJ", "TB_BL_BILL", "TB_BL_DUN_HIST", "TB_CL_CLOSE",
            "TB_SY_USER", "TB_SY_CODE_GRP", "TB_SY_CODE", "TB_SY_OPER_STD",
            "TB_SY_ROLE", "TB_SY_USER_ROLE", "TB_BS_PART", "TB_SY_ACCS_LOG", "TB_SY_CHG_HIST");
        assertThat(schema).doesNotContain("FOREIGN KEY", "ALTER TABLE", "DROP TABLE", "TRUNCATE");
        assertThat(schema).contains("pg_advisory_xact_lock(734901, 1)");
        String seed = resources.getAllValues().get(1);
        assertThat(java.util.regex.Pattern.compile("\\('[GM][0-9]{3}',").matcher(seed).results().count()).isEqualTo(40);
        assertThat(seed).contains("IF NOT EXISTS (SELECT 1 FROM TB_SY_MENU)", "LOCK TABLE TB_SY_MENU IN EXCLUSIVE MODE");
        String baseSeed = resources.getAllValues().get(2);
        for (String table : new String[]{"TB_SY_USER", "TB_SY_CODE_GRP", "TB_SY_CODE", "TB_SY_OPER_STD"}) {
            assertThat(baseSeed).contains("IF NOT EXISTS (SELECT 1 FROM " + table + ")", "LOCK TABLE " + table + " IN EXCLUSIVE MODE");
        }
        assertThat(baseSeed).contains("('CASH_BASE_YM', '202601',", "('PREF_TYPE', 'MENU_FOLD',");
        assertThat(baseSeed).doesNotContain("TB_SY_ROLE", "TB_SY_CALC_RULE", "DELETE", "UPDATE", "TRUNCATE");
        String userSeed = resources.getAllValues().get(3);
        for (String table : new String[]{"TB_SY_ROLE", "TB_SY_USER_ROLE", "TB_BS_PART"}) {
            assertThat(userSeed).contains("IF NOT EXISTS (SELECT 1 FROM " + table + ")",
                "LOCK TABLE " + table + " IN EXCLUSIVE MODE");
        }
        assertThat(userSeed).contains("('admin', 'ADMIN', 'SYSTEM')", "('HQ', '본사', 'Y', 'Y', 'SYSTEM')");
        assertThat(userSeed).doesNotContain("ON CONFLICT", "UPDATE ", "DELETE ", "TB_SY_ROLE_MENU_AUTH");
        String permissions = resources.getAllValues().get(4);
        assertThat(permissions).contains("IF NOT EXISTS (SELECT 1 FROM TB_SY_ROLE_MENU_AUTH)",
            "LOCK TABLE TB_SY_ROLE_MENU_AUTH IN EXCLUSIVE MODE");
    }

    @Test
    void everyPreparedTableIsPhysicalAndScreenSectionNamesCannotBecomeTables() throws Exception {
        preparation.prepare();
        var resources = ArgumentCaptor.forClass(String.class);
        verify(statement, times(migrationCount())).execute(resources.capture());
        var tablePattern = java.util.regex.Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)\\s*\\(");
        // V12's regexp_replace search pattern is not a physical table definition.
        assertThat(tablePattern.matcher("CREATE TABLE IF NOT EXISTS [A-Z_]+").find()).isFalse();
        var tables = resources.getAllValues().stream()
            .flatMap(sql -> tablePattern.matcher(sql).results().map(match -> match.group(1).toUpperCase(java.util.Locale.ROOT)))
            .toList();
        assertThat(tables).hasSize(tableDefinitionCount(tablePattern)).doesNotHaveDuplicates();
        // 개수는 파일에서 읽지만, 표 이름은 여기 그대로 적어 두어 표 하나가 빠지거나 바뀌면 바로 실패한다.
        assertThat(tables).containsExactly(
            "TB_SY_USER_SCOPE", "TB_SY_MENU", "TB_SY_USER_PREF", "TB_SY_NOTI", "TB_BS_PART_STD", "TB_PJ_PROJ",
            "TB_BL_BILL", "TB_BL_DUN_HIST", "TB_CL_CLOSE", "TB_SY_USER", "TB_SY_CODE_GRP", "TB_SY_CODE",
            "TB_SY_OPER_STD", "TB_SY_ROLE", "TB_SY_USER_ROLE", "TB_BS_PART", "TB_SY_ACCS_LOG", "TB_SY_CHG_HIST",
            "TB_SY_ROLE_MENU_AUTH",
            "TB_SY_CODE_MAP", "TB_SY_CALC_RULE",
            "TB_BS_CLNT", "TB_SY_UPLD", "TB_SY_UPLD_ERR", "TB_SY_ADM_AREA", "TB_HR_DEV", "TB_PJ_ASGN",
            "TB_PJ_PROJ_PART", "TB_CO_PART_SETL",
            "TB_HR_DEV_PRC", "TB_HR_DEV_SKILL", "TB_HR_DEV_STAT", "TB_HR_MON_PAY", "TB_SY_ADM_HDONG",
            "TB_PJ_PROJ_CHG", "TB_PJ_MNPW_PLAN", "TB_PJ_STEP", "TB_PJ_WEEK_MEMO", "TB_PJ_ISSUE", "TB_PJ_TK_DSTRB",
            "TB_PJ_DRCT_EXPS", "TB_PJ_ASGN_HIST", "TB_PJ_ASGN_MON", "TB_CO_MON_COST",
            "TB_SL_OPP", "TB_SL_OPP_NEED", "TB_SL_TENT_ASGN", "TB_SL_RCMD_LOG", "TB_BL_RCPT");
        assertThat(tables).isEqualTo(MigrationResources.tableNames());
        assertThat(tables).allMatch(name -> name.startsWith("TB_"));
        assertThat(tables).contains("TB_SY_CALC_RULE", "TB_SY_CHG_HIST", "TB_SY_CODE", "TB_SY_CODE_GRP",
            "TB_SY_CODE_MAP", "TB_SY_MENU", "TB_SY_OPER_STD", "TB_SY_ROLE_MENU_AUTH");
        assertThat(tables).doesNotContain("UI_24_SLICE_006", "U_I_24_S_L_I_C_E_006");
        String contract = resources.getAllValues().get(8);
        assertThat(contract).contains("required physical table missing", "c.convalidated",
            "uk_tb_sy_code_map_01", "uk_tb_sy_role_01", "ERRCODE='P0001'");
        assertThat(contract).doesNotContain("CREATE TABLE", "ALTER TABLE", "FOREIGN KEY", "INSERT INTO");
    }

    @Test
    void finalEntityContractFailureRollsBackSchemaAndSeeds() throws Exception {
        when(statement.execute(anyString())).thenReturn(true, true, true, true, true, true, true, true)
            .thenThrow(new SQLException("tb_sy_code_map.uk_tb_sy_code_map_01 expected columns", "P0001"));
        assertThatThrownBy(preparation::prepare).isInstanceOf(SQLException.class);
        verify(statement, times(9)).execute(anyString());
        verify(session).rollback(true);
        verify(session, never()).commit(anyBoolean());
    }

    @Test
    void finalUserSeedFailureRollsBackEntirePreparation() throws Exception {
        when(statement.execute(anyString())).thenReturn(true, true, true)
            .thenThrow(new SQLException("injected user seed failure"));
        assertThatThrownBy(preparation::prepare).isInstanceOf(SQLException.class);
        verify(statement, times(4)).execute(anyString());
        verify(session).rollback(true);
        verify(session, never()).commit(anyBoolean());
        verify(session).close();
    }

    @Test
    void nonStructuralErrorsDoNotLeakDatabaseSecrets() {
        assertThat(SchemaPreparation.diagnostic(new SQLException("credential-and-server-details", "08001")))
            .doesNotContain("credential", "server-details");
    }

    @Test
    void structuralDifferencesAreReportedForHumanCorrection() {
        assertThat(SchemaPreparation.diagnostic(new SQLException("tb_sy_menu.menu_id expected varchar(20) actual text", "P0001")))
            .contains("tb_sy_menu.menu_id", "expected", "actual");
    }
}
