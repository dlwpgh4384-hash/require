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
                    return new Result("원천 인력 수는 **" + count + "명**입니다.", List.of(new Table(null, List.of(Column.count("count", "원천 인력 수")),
                        List.of(List.of(count)), null, null)), null, List.of(), "인력 운영 보드 · 2026-01", query.period().endMonth(), false, false, List.of());
                }
            };
            var reader = new AiAnalysisReadExecutor(List.of(source), manager, new AiAnalysisPseudonymizer(mapper, json), new DisconnectedAiAnalysisGateway(), projection);
            var service = new CommonAiAnalysisService(new AiAnalysisInterpreter(calendar, AiAnalysisInterpreterTest.screens()), reader, writer, projection, new AiAnalysisProgress(),
                new AiAnalysisArchive(mapper, actor, projection, json, new ExcelExportService(), calendar));
            var before = fixture.businessSnapshot(); var first = service.postCommonAiAnalysisQuestion(new Input("read-one", "이번 달 매출", "UI-26", null));
            assertThat(first.answerStatus()).isEqualTo("OK"); fail.set(true);
            var error = service.postCommonAiAnalysisQuestion(new Input("read-error", "이번 달 매출", "UI-26", null)); assertThat(error.answerStatus()).isEqualTo("ERR");
            assertThat(fixture.businessSnapshot()).isEqualTo(before);
            var anotherSession = new SqlSessionTemplate(factory).getMapper(AiAnalysisMapper.class);
            // 같은 질문 글은 가장 최근 하나만(ERR).
            assertThat(anotherSession.history("admin")).hasSize(1).extracting(HistoryItem::answerStatus).containsExactly("ERR");
            assertThat(json.readTree(anotherSession.saved("admin", first.questionId())).toString()).isEqualTo(json.valueToTree(first).toString());
            assertThat(json.readTree(anotherSession.saved("admin", error.questionId())).path("answerStatus").asText()).isEqualTo("ERR");
            assertThat(anotherSession.history("other")).isEmpty(); assertThat(anotherSession.saved("other", first.questionId())).isNull();
        }
    }
    @Test void historyIsOwnLatestTwentyDistinctQuestionsEvenWithAnotherUsersRows() throws Exception {
        Assumptions.assumeTrue(System.getenv("NAVIGATION_TEST_DB_URL") != null, "Runner PostgreSQL settings are absent");
        try (var fixture = new ManpowerBoardFixture()) {
            var factory = fixture.factory();
            var session = new SqlSessionTemplate(factory); var mapper = session.getMapper(AiAnalysisMapper.class);
            var jdbc = new JdbcTemplate(factory.getConfiguration().getEnvironment().getDataSource());
            jdbc.update("DELETE FROM TB_SY_AI_QNA WHERE USER_ID IN ('admin','other')");
            for (int i = 1; i <= 25; i++)
                assertThat(mapper.insert(new com.company.common.aianalysis.mapper.AiAnalysisRows.Write(mapper.nextId(), "admin", "질문 " + i, "{}", "자료", "202611", "N", "OK"))).isEqualTo(1);
            String repeated = mapper.nextId();
            mapper.insert(new com.company.common.aianalysis.mapper.AiAnalysisRows.Write(repeated, "admin", "질문 3", "{}", "자료", "202611", "N", "NO_ANS"));
            mapper.insert(new com.company.common.aianalysis.mapper.AiAnalysisRows.Write(mapper.nextId(), "other", "남의 질문", "{}", "자료", "202611", "N", "OK"));
            var items = mapper.history("admin");
            assertThat(items).hasSize(20);
            assertThat(items.get(0).questionId()).isEqualTo(repeated); assertThat(items.get(0).answerStatus()).isEqualTo("NO_ANS");
            assertThat(items).extracting(HistoryItem::question).doesNotHaveDuplicates().doesNotContain("남의 질문");
            assertThat(items).extracting(HistoryItem::question).filteredOn("질문 3"::equals).hasSize(1);
            assertThat(mapper.history("other")).extracting(HistoryItem::question).containsExactly("남의 질문");
            jdbc.update("DELETE FROM TB_SY_AI_QNA WHERE USER_ID IN ('admin','other')");
        }
    }
}
