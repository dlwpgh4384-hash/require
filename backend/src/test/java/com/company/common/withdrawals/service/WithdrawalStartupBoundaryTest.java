package com.company.common.withdrawals.service;

import com.company.common.assignments.service.AssignmentMonthSchedule;
import com.company.common.assignments.service.CommonAssignmentService;
import com.company.common.schema.SchemaPreparation;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WithdrawalStartupBoundaryTest {
    @Test void restartingContextNeverCallsMonthlyRefreshOrSchemaPreparation() {
        var service = mock(CommonAssignmentService.class);
        var factory = mock(SqlSessionFactory.class);
        var dataSource = mock(DataSource.class);
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
        var runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class,
                SqlInitializationAutoConfiguration.class))
            .withPropertyValues("spring.flyway.enabled=false", "spring.sql.init.mode=never")
            .withBean(DataSource.class, () -> dataSource)
            .withBean(SchemaPreparation.class, () -> new SchemaPreparation(factory))
            .withBean(AssignmentMonthSchedule.class, () -> new AssignmentMonthSchedule(service));
        for (int restart = 0; restart < 2; restart++) {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(AssignmentMonthSchedule.class);
                assertThat(context).doesNotHaveBean(org.flywaydb.core.Flyway.class);
                verifyNoInteractions(service, factory, dataSource);
            });
        }
    }

    @Test void onlyExistingFirstOfMonthKoreanScheduleInvokesRefresh() throws Exception {
        var service = mock(CommonAssignmentService.class);
        var method = AssignmentMonthSchedule.class.getMethod("recalculate");
        var schedule = method.getAnnotation(Scheduled.class);
        assertThat(schedule).isNotNull();
        assertThat(schedule.cron()).isEqualTo("0 0 2 1 * *");
        assertThat(schedule.zone()).isEqualTo("Asia/Seoul");
        assertThat(schedule.initialDelay()).isEqualTo(-1);
        new AssignmentMonthSchedule(service).recalculate();
        verify(service).refreshCurrentMonth();
        verifyNoMoreInteractions(service);
    }
}
