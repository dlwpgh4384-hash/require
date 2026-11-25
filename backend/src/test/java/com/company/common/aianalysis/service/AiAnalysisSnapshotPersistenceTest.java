package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.mapper.AiAnalysisMapper;
import com.company.common.common.ManpowerBoardFixture;
import com.company.common.roles.mapper.RoleMapper;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAnalysisSnapshotPersistenceTest {
    @Test void committedSnapshotsSurviveNewSessionAndSourceRollbackWithoutChangingBusinessRows() throws Exception {
        Assumptions.assumeTrue(System.getenv("NAVIGATION_TEST_DB_URL") != null, "Runner PostgreSQL settings are absent");
        try (var fixture = new ManpowerBoardFixture()) {
            var factory = fixture.factory();
            var dataSource = factory.getConfiguration().getEnvironment().getDataSource();
            factory.getConfiguration().setEnvironment(new Environment("analysis", new SpringManagedTransactionFactory(), dataSource));
            var session = new SqlSessionTemplate(factory); var mapper = session.getMapper(AiAnalysisMapper.class);
            var manager = new DataSourceTransactionManager(dataSource); var json = new ObjectMapper().findAndRegisterModules();
            var actor = new CurrentBusinessActor(); var calendar = new BusinessCalendar(Clock.fixed(Instant.parse("2026-01-05T00:00:00Z"), ZoneOffset.UTC));
            var projection = new AiAnalysisProjection(session.getMapper(RoleMapper.class), actor);
            var writerFactory = new ProxyFactory(new AiAnalysisSnapshotWriter(mapper, json));
            writerFactory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
            var writer = (AiAnalysisSnapshotWriter) writerFactory.getProxy();
            var fail = new AtomicBoolean(); var jdbc = new JdbcTemplate(dataSource);
            AiAnalysisFunction source = new AiAnalysisFunction() {
                public Set<String> functions() { return Set.of("F-01"); }
                public Result read(AiAnalysisInterpreter.Query query) {
                    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                    assertThat(jdbc.queryForObject("show transaction_isolation", String.class)).isEqualTo("repeatable read");
                    long count = jdbc.queryForObject("select count(*) from TB_HR_DEV", Long.class);
                    if (fail.get()) throw new IllegalStateException("Source failure");
                    return new Result(new Table(List.of(new Column("count", "원천 인력 수")), List.of(List.of(count))), query.period(), false, "UI-28", List.of());
                }
            };
            var reader = new AiAnalysisReadExecutor(List.of(source), manager, new AiAnalysisPseudonymizer(mapper, json), new DisconnectedAiAnalysisGateway(), projection);
            var service = new CommonAiAnalysisService(new AiAnalysisInterpreter(calendar), reader, writer, projection, new AiAnalysisProgress(),
                new AiAnalysisArchive(mapper, actor, projection, json, new ExcelExportService(), calendar));
            var before = fixture.businessSnapshot(); var first = service.postCommonAiAnalysisQuestion(new Input("read-one", "이번 달 매출", "UI-26", null));
            assertThat(first.answerStatus()).isEqualTo("OK"); fail.set(true);
            var error = service.postCommonAiAnalysisQuestion(new Input("read-error", "이번 달 매출", "UI-26", null)); assertThat(error.answerStatus()).isEqualTo("ERR");
            assertThat(fixture.businessSnapshot()).isEqualTo(before);
            var anotherSession = new SqlSessionTemplate(factory).getMapper(AiAnalysisMapper.class);
            assertThat(anotherSession.history("admin")).hasSize(2).extracting(HistoryItem::answerStatus).containsExactly("ERR", "OK");
            assertThat(json.readTree(anotherSession.saved("admin", first.questionId()))).isEqualTo(json.valueToTree(first));
            assertThat(json.readTree(anotherSession.saved("admin", error.questionId())).path("answerStatus").asText()).isEqualTo("ERR");
            assertThat(anotherSession.history("other")).isEmpty(); assertThat(anotherSession.saved("other", first.questionId())).isNull();
        }
    }
}
