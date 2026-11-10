package com.cost.shell;

import com.cost.shell.db.MenuSchemaInspector;
import com.cost.shell.menu.MenuMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in real PostgreSQL 16 tests. Each method owns and drops only its UUID schema. */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PostgreSqlPersistenceTest {
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("TEST_DATABASE_URL"),
            System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
    }

    private Flyway flyway(String schema, boolean baseline) {
        return Flyway.configure().dataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"))
            .schemas(schema).defaultSchema(schema).locations("classpath:db/migration")
            .baselineOnMigrate(baseline).baselineVersion("0").load();
    }

    @Test void migrationSeedsOnceMapsRealRowsAndDoesNotRestoreAnEmptiedTableAfterRestart() throws Exception {
        String schema = "test_menu_" + UUID.randomUUID().toString().replace("-", "");
        try {
            flyway(schema, false).migrate();
            try (var connection = connect(); var statement = connection.createStatement()) {
                statement.execute("SET search_path TO " + schema);
                assertThat(MenuSchemaInspector.inspect(connection)).isEmpty();
                var configuration = new Configuration();
                try (var input = new ClassPathResource("mappers/MenuMapper.xml").getInputStream()) {
                    new XMLMapperBuilder(input, configuration, "mappers/MenuMapper.xml", configuration.getSqlFragments()).parse();
                }
                try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
                    var rows = session.getMapper(MenuMapper.class).findAll();
                    assertThat(rows).hasSize(40);
                    assertThat(rows.stream().filter(row -> row.menuId().equals("M001")).findFirst().orElseThrow().scrnId())
                        .isEqualTo("UI-16");
                }
            }
            flyway(schema, false).migrate();
            try (var connection = connect(); var statement = connection.createStatement()) {
                statement.execute("DELETE FROM " + schema + ".tb_sy_menu");
            }
            flyway(schema, false).migrate();
            try (var connection = connect(); var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM " + schema + ".tb_sy_menu")) {
                result.next();
                assertThat(result.getInt(1)).isZero();
            }
        } finally {
            try (var connection = connect(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @Test void existingNonemptyTableAndWiderColumnAreReportedAndPreserved() throws Exception {
        String schema = "test_menu_" + UUID.randomUUID().toString().replace("-", "");
        String id = UUID.randomUUID().toString().substring(0, 20);
        try {
            try (var connection = connect(); var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
                statement.execute("SET search_path TO " + schema);
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/schema.sql"));
                statement.execute("ALTER TABLE tb_sy_menu ALTER COLUMN menu_nm TYPE VARCHAR(80)");
                try (var insert = connection.prepareStatement("""
                    INSERT INTO tb_sy_menu(menu_id, menu_nm, menu_lvl, sort_ord, reg_user_id)
                    VALUES (?, ?, 1, 1, ?)
                    """)) {
                    insert.setString(1, id);
                    insert.setString(2, "독립 소유 데이터");
                    insert.setString(3, id);
                    insert.executeUpdate();
                }
                assertThat(MenuSchemaInspector.inspect(connection).toString()).contains("menu_nm", "80");
            }
            flyway(schema, true).migrate();
            try (var connection = connect(); var statement = connection.createStatement()) {
                statement.execute("SET search_path TO " + schema);
                try (var result = statement.executeQuery("SELECT menu_id FROM tb_sy_menu")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo(id);
                    assertThat(result.next()).isFalse();
                }
                assertThat(MenuSchemaInspector.inspect(connection).toString()).contains("menu_nm", "80");
            }
        } finally {
            try (var connection = connect(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
