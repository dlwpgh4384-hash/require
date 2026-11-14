package com.company.common.schema;

import com.company.common.schema.SchemaPreparation;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuPreparationBoundaryTest {
    private static final List<String> RESOURCES = List.of(
        "V1__navigation_schema.sql", "V2__navigation_menu_seed.sql",
        "V3__common_function_seed.sql", "V4__user_management_seed.sql",
        "V5__role_permissions.sql", "V6__role_schema_contract.sql",
        "V7__code_definitions_schema.sql", "V8__calculation_rule_seed.sql",
        "V9__common_entity_contract.sql", "V10__client_part_upload_schema.sql");
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
    void normalBeanStartupDoesNotRunFlywaySqlInitializationOrPreparation() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
        var dataSource = mock(DataSource.class);
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class,
                SqlInitializationAutoConfiguration.class))
            .withPropertyValues("spring.flyway.enabled=" + properties.getProperty("spring.flyway.enabled"),
                "spring.sql.init.mode=" + properties.getProperty("spring.sql.init.mode"))
            .withBean(DataSource.class, () -> dataSource)
            .withBean(SchemaPreparation.class, () -> preparation)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(SchemaPreparation.class);
                assertThat(context).doesNotHaveBean(org.flywaydb.core.Flyway.class);
                verifyNoInteractions(factory, session, connection, statement, dataSource);
            });
    }

    @Test
    void explicitPreparationRunsExactlyThePackagedResourcesInOrder() throws Exception {
        preparation.prepare();
        var sql = ArgumentCaptor.forClass(String.class);
        var order = inOrder(statement, session);
        order.verify(statement, times(RESOURCES.size())).execute(sql.capture());
        order.verify(session).commit(true);
        order.verify(session).close();
        for (int index = 0; index < RESOURCES.size(); index++) {
            assertThat(sql.getAllValues().get(index)).isEqualTo(resource(RESOURCES.get(index)));
        }
        verify(session, never()).rollback(anyBoolean());
        var packaged = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:db/migration/V*__*.sql");
        assertThat(Arrays.stream(packaged).map(item -> item.getFilename()).toList())
            .containsExactlyInAnyOrderElementsOf(RESOURCES);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, 7, 8, 9, 10})
    void permissionPreparationFailureRollsBackTheWholePreparation(int failingResource) throws Exception {
        int[] executions = {0};
        when(statement.execute(anyString())).thenAnswer(call -> {
            if (++executions[0] == failingResource) throw new SQLException("injected failure", "P0001");
            return true;
        });
        assertThatThrownBy(preparation::prepare).isInstanceOf(SQLException.class);
        verify(statement, times(failingResource)).execute(anyString());
        verify(session).rollback(true);
        verify(session, never()).commit(anyBoolean());
        verify(session).close();
    }

    @Test
    void packagedPhysicalInventoryStaysAtTheExistingTablesWithoutScreenEntities() throws Exception {
        var names = new java.util.LinkedHashSet<String>();
        var createTable = Pattern.compile("CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+(TB_[A-Z_]+)");
        for (String filename : RESOURCES) {
            var tables = createTable.matcher(resource(filename));
            while (tables.find()) names.add(tables.group(1));
        }
        assertThat(names).containsExactlyInAnyOrder("TB_SY_USER_SCOPE", "TB_SY_MENU", "TB_SY_USER_PREF",
            "TB_SY_NOTI", "TB_BS_PART_STD", "TB_PJ_PROJ", "TB_BL_BILL", "TB_BL_DUN_HIST", "TB_CL_CLOSE",
            "TB_SY_USER", "TB_SY_CODE_GRP", "TB_SY_CODE", "TB_SY_OPER_STD", "TB_SY_ROLE", "TB_SY_USER_ROLE",
            "TB_BS_PART", "TB_SY_ACCS_LOG", "TB_SY_CHG_HIST", "TB_SY_ROLE_MENU_AUTH",
            "TB_SY_CODE_MAP", "TB_SY_CALC_RULE", "TB_BS_CLNT", "TB_SY_UPLD", "TB_SY_UPLD_ERR",
            "TB_SY_ADM_AREA", "TB_HR_DEV", "TB_PJ_ASGN", "TB_PJ_PROJ_PART", "TB_CO_PART_SETL");
    }

    @Test
    void menuAndPermissionDefinitionsKeepTheirPhysicalKeysFlagsAndZonedAuditTimes() throws Exception {
        String schema = resource(RESOURCES.get(0));
        String menu = schema.substring(schema.indexOf("CREATE TABLE IF NOT EXISTS TB_SY_MENU ("));
        menu = menu.substring(0, menu.indexOf(")$ddl$"));
        assertThat(menu).contains("MENU_ID VARCHAR(20) NOT NULL", "MENU_NM VARCHAR(50) NOT NULL",
            "CONSTRAINT PK_TB_SY_MENU PRIMARY KEY (MENU_ID)",
            "REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL", "MOD_DTTM TIMESTAMPTZ",
            "CHECK (USE_YN IN ('Y', 'N'))", "CHECK (PC_ONLY_YN IN ('Y', 'N'))",
            "CHECK (LOCK_YN IN ('Y', 'N'))");
        assertThat(menu).doesNotContain("UNIQUE", "REFERENCES", "FOREIGN KEY");
        String permissions = resource(RESOURCES.get(4));
        assertThat(permissions).contains("PRIMARY KEY (ROLE_ID, MENU_ID)",
            "REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL", "MOD_DTTM TIMESTAMPTZ");
        for (String flag : List.of("INQ_YN", "REG_YN", "UPD_YN", "DEL_YN", "UPLD_YN", "DNLD_YN", "CNFM_YN")) {
            assertThat(permissions).contains(flag + " CHAR(1) DEFAULT 'N' NOT NULL",
                "CHECK (" + flag + " IN ('Y', 'N'))");
        }
        assertThat(schema).contains("HIST_ID NUMERIC(12) DEFAULT nextval('SQ_TB_SY_CHG_HIST') NOT NULL");
    }

    private static String resource(String filename) throws Exception {
        return new ClassPathResource("db/migration/" + filename).getContentAsString(StandardCharsets.UTF_8);
    }
}
