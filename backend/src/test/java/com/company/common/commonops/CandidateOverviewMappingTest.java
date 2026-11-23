package com.company.common.commonops;

import com.company.common.commonops.dto.CandidateOverviewContracts.Summary;
import com.company.common.commonops.mapper.CandidateOverviewMapper;
import com.company.common.commonops.mapper.CandidateOverviewMapper.*;
import com.company.common.config.MyBatisConfig;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Map;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CandidateOverviewMappingTest {
    private static final String NS = CandidateOverviewMapper.class.getName() + ".";
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/commonops/CandidateOverviewMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }
    @Test void everyProjectionUsesActualConstructorMaterializationWithoutDatabase() throws Exception {
        materialize("summary", new Summary(9, 3, 4, 2));
        materialize("activeCandidates", new CandidateCount("DEV", LocalDate.of(2027,1,2)));
        materialize("dictionary", new Dictionary("RSM_LABEL", "label", "희망금액", "WANT"));
        materialize("companies", new Company("company", "협력사"));
        materialize("areas", new Area("area", "서울", "서울특별시", "강남구"));
    }
    private void materialize(String id, Record expected) throws Exception {
        var statement = configuration().getMappedStatement(NS + id);
        var mappings = statement.getResultMaps().get(0).getConstructorResultMappings();
        var components = expected.getClass().getRecordComponents();
        assertThat(mappings).hasSize(components.length);
        var metadata = new RowSetMetaDataImpl(); metadata.setColumnCount(mappings.size());
        for (int i = 0; i < mappings.size(); i++) {
            var field = mappings.get(i);
            assertThat(field.getColumn()).isEqualTo(components[i].getName());
            metadata.setColumnName(i + 1, field.getColumn()); metadata.setColumnLabel(i + 1, field.getColumn());
            metadata.setColumnType(i + 1, field.getJavaType() == int.class ? Types.INTEGER :
                field.getJavaType() == LocalDate.class ? Types.DATE : Types.VARCHAR);
        }
        try (var rows = RowSetProvider.newFactory().createCachedRowSet()) {
            rows.setMetaData(metadata); rows.moveToInsertRow();
            for (int i = 0; i < components.length; i++) {
                Object value = components[i].getAccessor().invoke(expected);
                rows.updateObject(i + 1, value instanceof LocalDate date ? Date.valueOf(date) : value);
            }
            rows.insertRow(); rows.moveToCurrentRow(); rows.beforeFirst();
            ResultSet jdbcRows = mock(ResultSet.class, org.mockito.AdditionalAnswers.delegatesTo(rows));
            doReturn(false).when(jdbcRows).isClosed();
            for (var component : components) if (component.getType() == LocalDate.class)
                doReturn(component.getAccessor().invoke(expected)).when(jdbcRows).getObject(component.getName(), LocalDate.class);
            Statement jdbc = mock(Statement.class);
            when(jdbc.getResultSet()).thenReturn(jdbcRows); when(jdbc.getUpdateCount()).thenReturn(-1);
            var parameters = Map.of("groupId", "JOB", "nextMonth", LocalDate.of(2027,1,1), "overdueBefore", LocalDate.of(2026,12,1));
            var handler = new DefaultResultSetHandler(null, statement, null, null, statement.getBoundSql(parameters), RowBounds.DEFAULT);
            assertThat(handler.handleResultSets(jdbc)).containsExactly(expected);
        }
    }
    @Test void packageIsAlreadyScannedAndNoMapperStatementCanWrite() throws Exception {
        assertThat(MyBatisConfig.class.getAnnotation(MapperScan.class).basePackages()).contains(CandidateOverviewMapper.class.getPackageName());
        var config = configuration();
        for (var method : CandidateOverviewMapper.class.getDeclaredMethods())
            assertThat(config.getMappedStatement(NS + method.getName()).getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
    }
    @Test void aggregateAndCountQueriesExcludeBothTerminalStatusesAndUseStrictOverdueBoundary() throws Exception {
        var config = configuration();
        var dates = new Dates(LocalDate.of(2026,12,31), LocalDate.of(2027,1,1), LocalDate.of(2026,12,1));
        String summary = config.getMappedStatement(NS + "summary").getBoundSql(dates).getSql();
        String counts = config.getMappedStatement(NS + "activeCandidates").getBoundSql(Map.of()).getSql();
        assertThat(summary).contains("CNTC_STAT_CD NOT IN ('HOLD','CTRT')", "LAST_CNTC_DT < ?", "INPUT_PSBL_DT <= ?");
        assertThat(counts).contains("CNTC_STAT_CD NOT IN ('HOLD','CTRT')");
    }
}
