package com.company.common.salesopportunities;

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

class SalesOpportunityMappingTest {
    @ParameterizedTest
    @ValueSource(strings = {"select", "lock", "needs", "history", "reservations", "developerReservations"})
    void everyProjectionMaterializesThroughProductionConstructorMappings(String operation) throws Exception {
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
        var resource = new ClassPathResource("mapper/salesopportunities/SalesOpportunityMapper.xml");
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource.getPath(), configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement("com.company.common.salesopportunities.mapper.SalesOpportunityMapper." + operation);
        var resultMap = statement.getResultMaps().get(0);
        var mappings = resultMap.getConstructorResultMappings();
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(mappings.size());
        List<Object> expected = new ArrayList<>();
        for (int i = 0; i < mappings.size(); i++) {
            var mapping = mappings.get(i); Class<?> type = mapping.getJavaType();
            metadata.setColumnName(i + 1, mapping.getColumn()); metadata.setColumnLabel(i + 1, mapping.getColumn());
            metadata.setColumnType(i + 1, type == java.math.BigDecimal.class ? Types.NUMERIC
                : type == java.time.LocalDate.class ? Types.DATE : type == java.time.Instant.class ? Types.TIMESTAMP
                : type == int.class || type == Integer.class ? Types.INTEGER : type == long.class ? Types.BIGINT : Types.VARCHAR);
            Object value;
            if (type == java.math.BigDecimal.class) value = new java.math.BigDecimal((i + 101) + ".25");
            else if (type == java.time.LocalDate.class) value = java.time.LocalDate.of(2026, 2, (i % 20) + 1);
            else if (type == java.time.Instant.class) value = java.time.Instant.parse("2026-02-15T12:34:56Z").plusSeconds(i);
            else if (type == Integer.class || type == int.class) value = i + 17;
            else if (type == long.class) value = (long)i + 41;
            else value = "값-" + mapping.getColumn();
            expected.add(value);
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
            var parameter = java.util.Map.of("id", "opportunity", "developerId", "developer");
            var handler = new DefaultResultSetHandler(null, statement, null, null, statement.getBoundSql(parameter), RowBounds.DEFAULT);
            List<Object> actual = handler.handleResultSets(jdbc);
            assertThat(actual).hasSize(1);
            assertThat(actual.get(0)).isEqualTo(resultMap.getType().getDeclaredConstructors()[0].newInstance(expected.toArray()));
        }
    }
}
