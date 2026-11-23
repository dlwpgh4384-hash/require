package com.company.common.common;

import com.company.common.schema.SchemaPreparation;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessPlanPreparationTest {
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
    void explicitPreparationExecutesPackagedBusinessPlanSchemaBeforeCommit() throws Exception {
        preparation.prepare();
        var resources = ArgumentCaptor.forClass(String.class);
        int count = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:db/migration/V*__*.sql").length;
        verify(statement, times(count)).execute(resources.capture());
        String businessPlan = new ClassPathResource("db/migration/V18__business_plan_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(resources.getAllValues()).containsOnlyOnce(businessPlan);
        var ordered = inOrder(statement, session);
        for (String sql : resources.getAllValues()) {
            ordered.verify(statement).execute(sql);
            ordered.verify(statement).close();
        }
        ordered.verify(session).commit(true);
        ordered.verify(session).close();
        verify(session, never()).rollback(anyBoolean());
    }

    @Test
    void businessPlanRefusalRollsBackEntirePreparationWithoutCommit() throws Exception {
        when(statement.execute(contains("DO $business_plan_schema$")))
            .thenThrow(new SQLException("tb_fi_biz_plan: original columns differ", "P0001"));
        assertThatThrownBy(preparation::prepare)
            .isInstanceOf(SQLException.class).hasMessageContaining("tb_fi_biz_plan");
        verify(session).rollback(true);
        verify(session, never()).commit(anyBoolean());
        verify(session).close();
    }

    @Test
    void ordinaryServerConfigurationKeepsAutomaticSchemaInitializationDisabled() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }
}
