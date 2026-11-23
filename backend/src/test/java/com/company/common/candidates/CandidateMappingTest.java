package com.company.common.candidates;

import com.company.common.candidates.dto.CandidateContracts.*;
import java.sql.Types;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CandidateMappingTest {
    /** 등록 시각은 DB 기본값에 맡기지 않고 clock_timestamp()로 직접 넣는다. */
    @ParameterizedTest
    @ValueSource(strings = {"insertCandidate", "insertSkill", "insertContact"})
    void insertsWriteRegistrationTimeExplicitly(String operation) throws Exception {
        String xml = new ClassPathResource("mapper/candidates/CandidateMapper.xml").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var block = java.util.regex.Pattern.compile("<insert id=\"" + operation + "\">(.*?)</insert>", java.util.regex.Pattern.DOTALL).matcher(xml);
        assertThat(block.find()).isTrue();
        assertThat(block.group(1)).contains("reg_dttm").contains("clock_timestamp()");
    }
    @ParameterizedTest
    @ValueSource(strings = {"selectCandidates", "selectCandidate", "lockCandidate", "selectContacts", "selectArea", "selectSeats"})
    void explicitConstructorsMaterializeEveryFieldThroughRealMyBatis(String operation) throws Exception {
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
        var resource = new ClassPathResource("mapper/candidates/CandidateMapper.xml");
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource.getPath(), configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement("com.company.common.candidates.mapper.CandidateMapper." + operation);
        var resultMap = statement.getResultMaps().get(0);
        var mappings = resultMap.getConstructorResultMappings();
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(mappings.size());
        List<Object> expected = new ArrayList<>();
        for (int i = 0; i < mappings.size(); i++) {
            var mapping = mappings.get(i); Class<?> type = mapping.getJavaType();
            metadata.setColumnName(i + 1, mapping.getColumn()); metadata.setColumnLabel(i + 1, mapping.getColumn());
            metadata.setColumnType(i + 1, type == java.math.BigDecimal.class ? Types.NUMERIC
                : type == java.time.LocalDate.class ? Types.DATE : type == java.time.Instant.class ? Types.TIMESTAMP : Types.VARCHAR);
            expected.add(type == java.math.BigDecimal.class ? new java.math.BigDecimal((i + 101) + ".25")
                : type == java.time.LocalDate.class ? java.time.LocalDate.of(2026, 2, (i % 20) + 1)
                : type == java.time.Instant.class ? java.time.Instant.parse("2026-02-15T12:34:56Z").plusSeconds(i)
                : "값-" + mapping.getColumn());
        }
        try (var rows = RowSetProvider.newFactory().createCachedRowSet()) {
            rows.setMetaData(metadata); rows.moveToInsertRow();
            for (int i = 0; i < expected.size(); i++) {
                var value = expected.get(i);
                if (value instanceof java.time.LocalDate day) rows.updateDate(i + 1, java.sql.Date.valueOf(day));
                else if (value instanceof java.time.Instant instant) rows.updateTimestamp(i + 1, java.sql.Timestamp.from(instant));
                else rows.updateObject(i + 1, value);
            }
            rows.insertRow(); rows.moveToCurrentRow(); rows.beforeFirst();
            java.sql.ResultSet mappedRows = mock(java.sql.ResultSet.class, org.mockito.AdditionalAnswers.delegatesTo(rows));
            doReturn(false).when(mappedRows).isClosed();
            doAnswer(call -> {
                var day = rows.getDate((String) call.getArgument(0)); return day == null ? null : day.toLocalDate();
            }).when(mappedRows).getObject(anyString(), eq(java.time.LocalDate.class));
            Statement jdbc = mock(Statement.class); when(jdbc.getResultSet()).thenReturn(mappedRows); when(jdbc.getUpdateCount()).thenReturn(-1);
            Object parameter = operation.equals("selectCandidates") ? new Query(new Filter(null, null, null, null, null), null) : "candidate";
            var handler = new DefaultResultSetHandler(null, statement, null, null, statement.getBoundSql(parameter), RowBounds.DEFAULT);
            List<Object> actual = handler.handleResultSets(jdbc);
            assertThat(actual).hasSize(1);
            assertThat(actual.get(0)).isEqualTo(resultMap.getType().getDeclaredConstructors()[0].newInstance(expected.toArray()));
        }
    }
}
