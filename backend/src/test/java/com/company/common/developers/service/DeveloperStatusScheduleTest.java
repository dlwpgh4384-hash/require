package com.company.common.developers.service;

import com.company.common.developers.config.DeveloperConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.support.SimpleTriggerContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeveloperStatusScheduleTest {
    @Test
    void preparationContextDoesNotRefreshOrRegisterScheduledDatabaseWork() {
        CommonDeveloperService service = mock(CommonDeveloperService.class);
        new ApplicationContextRunner()
            .withUserConfiguration(DeveloperConfiguration.class, DeveloperStatusSchedule.class)
            .withBean(CommonDeveloperService.class, () -> service)
            .withBean(SqlSessionFactory.class, DeveloperStatusScheduleTest::sessions)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(DeveloperStatusSchedule.class);
                assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
                context.publishEvent(new ApplicationReadyEvent(new SpringApplication(),
                    new String[]{"--prepare-schema"}, context.getSourceApplicationContext(), Duration.ZERO));
                verifyNoInteractions(service);
            });
    }

    @Test
    void servletContextStillRefreshesOnReadyButDailyRefreshMovedToDailyBatch() {
        CommonDeveloperService service = mock(CommonDeveloperService.class);
        new WebApplicationContextRunner()
            .withUserConfiguration(DeveloperConfiguration.class, DeveloperStatusSchedule.class)
            .withBean(CommonDeveloperService.class, () -> service)
            .withBean(SqlSessionFactory.class, DeveloperStatusScheduleTest::sessions)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(DeveloperStatusSchedule.class);
                verifyNoInteractions(service);
                context.publishEvent(new ApplicationReadyEvent(new SpringApplication(),
                    new String[0], context.getSourceApplicationContext(), Duration.ZERO));
                verify(service).refreshAll();
                // 매일 02:00 갱신은 일 배치(DailyBatch) ①단계로 옮겼다(명세 ⑪-1): 이 부품에는 따로 도는 예약이 없다.
                assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
                verifyNoMoreInteractions(service);
            });
    }

    private static SqlSessionFactory sessions() {
        return new SqlSessionFactoryBuilder().build(new org.apache.ibatis.session.Configuration(
            new Environment("schedule-test", new JdbcTransactionFactory(), mock(DataSource.class))));
    }
}
