package com.company.common.aianalysis.mapper;

import com.company.common.aianalysis.dto.AiAnalysisContracts.HistoryItem;
import com.company.common.aianalysis.mapper.AiAnalysisRows.Identity;
import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AiAnalysisMappingTest {
    @Test void historyAndIdentityRowsUseDeclaredConstructorLabelsWithoutDatabase() throws Exception {
        var cfg = configuration(); var at = Instant.parse("2026-11-25T12:00:00Z");
        var history = materialize(cfg, "history", HistoryItem.class, List.of("questionId", "question", "askedAt", "answerStatus"),
            List.of("812", "매출", Timestamp.from(at), "OK"));
        assertThat(history).isEqualTo(new HistoryItem("812", "매출", at, "OK"));
        assertThat(materialize(cfg, "identities", Identity.class, List.of("name", "replacement"), List.of("개발자", "M-43")))
            .isEqualTo(new Identity("개발자", "M-43"));
    }
    @Test void everySqlPropertyIsDeclaredAndOwnReadsAlwaysUseServerActor() throws Exception {
        var cfg = configuration();
        assertThat(cfg.getMappedStatement(prefix() + "insert").getBoundSql(new AiAnalysisRows.Write("1", "admin", "매출", "{}", "자료", "202611", "N", "OK"))
            .getParameterMappings()).extracting(org.apache.ibatis.mapping.ParameterMapping::getProperty)
            .containsExactly("id", "actor", "question", "answer", "description", "baseMonth", "provisional", "status", "actor");
        for (String statement : List.of("history", "saved")) {
            var sql = cfg.getMappedStatement(prefix() + statement).getBoundSql(Map.of("actor", "admin", "id", "1")).getSql();
            assertThat(sql).contains("q.USER_ID = ?"); assertThat(sql).doesNotContain("REQUEST_ID");
        }
        var sql = cfg.getMappedStatement(prefix() + "history").getBoundSql(Map.of("actor", "admin")).getSql();
        assertThat(sql).contains("ORDER BY q.QSTN_DTTM DESC, q.QNA_ID DESC");
    }
    String prefix() { return AiAnalysisMapper.class.getName() + "."; }
    Configuration configuration() throws Exception {
        var cfg = new Configuration(); cfg.setMapUnderscoreToCamelCase(true);
        try (var stream = getClass().getClassLoader().getResourceAsStream("mapper/aianalysis/AiAnalysisMapper.xml")) {
            assertThat(stream).isNotNull(); new XMLMapperBuilder(stream, cfg, "aianalysis", cfg.getSqlFragments()).parse();
        }
        return cfg;
    }
    <T> T materialize(Configuration cfg, String name, Class<T> type, List<String> labels, List<Object> values) throws Exception {
        var statement = cfg.getMappedStatement(prefix() + name); var map = statement.getResultMaps().get(0);
        assertThat(map.getConstructorResultMappings()).extracting(org.apache.ibatis.mapping.ResultMapping::getColumn).containsExactlyElementsOf(labels);
        var rs = mock(ResultSet.class); var meta = mock(ResultSetMetaData.class); when(rs.getMetaData()).thenReturn(meta); when(meta.getColumnCount()).thenReturn(labels.size()); when(rs.next()).thenReturn(true, false);
        for (int i = 0; i < labels.size(); i++) {
            int index = i + 1; String label = labels.get(i); Object value = values.get(i);
            when(meta.getColumnLabel(index)).thenReturn(label); when(meta.getColumnName(index)).thenReturn(label); when(meta.getColumnClassName(index)).thenReturn(value.getClass().getName());
            when(meta.getColumnType(index)).thenReturn(value instanceof Timestamp ? Types.TIMESTAMP : Types.VARCHAR);
            when(rs.getString(label)).thenReturn(String.valueOf(value)); when(rs.getObject(label)).thenReturn(value);
            if (value instanceof Timestamp time) when(rs.getTimestamp(label)).thenReturn(time);
        }
        var jdbc = mock(Statement.class); when(jdbc.getResultSet()).thenReturn(rs);
        var handler = new DefaultResultSetHandler(mock(Executor.class), statement, null, null, new BoundSql(cfg, "select", List.of(), null), RowBounds.DEFAULT);
        return type.cast(handler.handleResultSets(jdbc).get(0));
    }
}
