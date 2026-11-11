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

    @BeforeEach
    void connect() throws Exception {
        when(factory.openSession(false)).thenReturn(session);
        when(session.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
    }

    @Test
    void bothPackagedResourcesExecuteBeforeSingleCommit() throws Exception {
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
    void migrationResourcesContainOnlyNineModelTablesAndOriginalMenuSeed() throws Exception {
        preparation.prepare();
        var resources = ArgumentCaptor.forClass(String.class);
        verify(statement, times(2)).execute(resources.capture());
        String schema = resources.getAllValues().get(0);
        var tables = java.util.regex.Pattern.compile("CREATE TABLE IF NOT EXISTS (TB_[A-Z_]+)").matcher(schema);
        var names = new java.util.ArrayList<String>();
        while (tables.find()) names.add(tables.group(1));
        assertThat(names).containsExactly("TB_SY_USER_SCOPE", "TB_SY_MENU", "TB_SY_USER_PREF", "TB_SY_NOTI", "TB_BS_PART_STD", "TB_PJ_PROJ", "TB_BL_BILL", "TB_BL_DUN_HIST", "TB_CL_CLOSE");
        assertThat(schema).doesNotContain("FOREIGN KEY", "ALTER TABLE", "DROP TABLE", "TRUNCATE");
        assertThat(schema).contains("pg_advisory_xact_lock(734901, 1)");
        String seed = resources.getAllValues().get(1);
        assertThat(java.util.regex.Pattern.compile("\\('[GM][0-9]{3}',").matcher(seed).results().count()).isEqualTo(40);
        assertThat(seed).contains("IF NOT EXISTS (SELECT 1 FROM TB_SY_MENU)", "LOCK TABLE TB_SY_MENU IN EXCLUSIVE MODE");
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
