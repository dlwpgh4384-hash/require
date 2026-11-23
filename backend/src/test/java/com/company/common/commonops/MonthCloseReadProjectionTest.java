package com.company.common.commonops;

import com.company.common.commonops.mapper.MonthCloseHistoryRow;
import com.company.common.commonops.mapper.MonthCloseReadMapper;
import com.company.common.commonops.service.MonthCloseReadService;
import com.company.common.config.MyBatisConfig;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthCloseReadProjectionTest {
    private static final String NS = MonthCloseReadMapper.class.getName() + ".";

    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/monthcloses/MonthCloseReadMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }

    @Test void productionConstructorMaterializesEveryHistoryFieldWithoutDatabase() throws Exception {
        var config = configuration();
        var statement = config.getMappedStatement(NS + "selectHistory");
        var fields = statement.getResultMaps().get(0).getConstructorResultMappings();
        var expected = new MonthCloseHistoryRow("202610", "CLOSE", "서버 처리자",
            Instant.parse("2026-11-01T01:02:03.123456Z"), "저장 사유", "MM 초과,영업 파트 정산");
        Object[] values = {expected.month(), expected.actionCode(), expected.processor(),
            expected.occurredAt(), expected.reason(), expected.remainingNames()};
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(fields.size());
        assertThat(fields).hasSize(MonthCloseHistoryRow.class.getRecordComponents().length);
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            assertThat(field.getColumn()).isEqualTo(MonthCloseHistoryRow.class.getRecordComponents()[i].getName());
            metadata.setColumnName(i + 1, field.getColumn());
            metadata.setColumnLabel(i + 1, field.getColumn());
            metadata.setColumnType(i + 1, field.getJavaType() == Instant.class ? Types.TIMESTAMP : Types.VARCHAR);
        }
        try (var rows = RowSetProvider.newFactory().createCachedRowSet()) {
            rows.setMetaData(metadata);
            rows.moveToInsertRow();
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof Instant time) rows.updateTimestamp(i + 1, Timestamp.from(time));
                else rows.updateObject(i + 1, values[i]);
            }
            rows.insertRow();
            rows.moveToCurrentRow();
            rows.beforeFirst();
            ResultSet jdbcRows = mock(ResultSet.class, org.mockito.AdditionalAnswers.delegatesTo(rows));
            doReturn(false).when(jdbcRows).isClosed();
            Statement jdbc = mock(Statement.class);
            when(jdbc.getResultSet()).thenReturn(jdbcRows);
            when(jdbc.getUpdateCount()).thenReturn(-1);
            var parameters = new HashMap<String, Object>();
            parameters.put("month", null);
            var handler = new DefaultResultSetHandler(null, statement, null, null,
                statement.getBoundSql(parameters), RowBounds.DEFAULT);
            assertThat(handler.handleResultSets(jdbc)).containsExactly(expected);
        }
    }

    @Test void optionalPredicateHasNoNullBindAndOrderingUsesOriginalCompositeIdentity() throws Exception {
        var statement = configuration().getMappedStatement(NS + "selectHistory");
        var parameters = new HashMap<String, Object>();
        parameters.put("month", null);
        var all = statement.getBoundSql(parameters);
        assertThat(all.getParameterMappings()).isEmpty();
        assertThat(all.getSql()).doesNotContain("WHERE");
        assertThat(all.getSql()).contains("ORDER BY h.reg_dttm DESC, h.close_ym DESC, h.hist_sn DESC");
        parameters.put("month", "202610");
        var filtered = statement.getBoundSql(parameters);
        assertThat(filtered.getSql()).contains("h.close_ym = ?");
        assertThat(filtered.getParameterMappings()).extracting(field -> field.getProperty()).containsExactly("month");
    }

    @Test void everyRegisteredStatementIsAReadAndClosedJudgementHasOneSource() throws Exception {
        var config = configuration();
        assertThat(MyBatisConfig.class.getAnnotation(MapperScan.class).basePackages())
            .contains(MonthCloseReadMapper.class.getPackageName());
        for (var method : MonthCloseReadMapper.class.getDeclaredMethods()) {
            assertThat(config.getMappedStatement(NS + method.getName()).getSqlCommandType())
                .isEqualTo(SqlCommandType.SELECT);
        }
        // The badge reads CASH_BASE_YM and closed months through UserPreferencePeriodSourceMapper, not its own query.
        assertThat(config.hasStatement(NS + "isClosed")).isFalse();
    }

    @Test void bothServiceBoundariesAreReadOnlyRepeatableRead() throws Exception {
        for (var method : List.of(
            MonthCloseReadService.class.getMethod("getCommonMonthCloseBadge"),
            MonthCloseReadService.class.getMethod("getCommonMonthCloseHistory", String.class))) {
            var boundary = method.getAnnotation(Transactional.class);
            assertThat(boundary.readOnly()).isTrue();
            assertThat(boundary.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        }
    }
}
