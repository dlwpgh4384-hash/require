package com.company.common.airecommendations.mapper;

import com.company.common.airecommendations.AiRecommendationConfiguration;
import com.company.common.airecommendations.dto.AiRecommendationOptionsContracts.Filter;
import com.company.common.airecommendations.mapper.AiRecommendationOptionsMapper.*;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
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

class AiRecommendationOptionsMappingTest {
    private static final String NS = AiRecommendationOptionsMapper.class.getName() + ".";

    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/airecommendations/AiRecommendationOptionsMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }

    @Test void targetProjectionUsesProductionMyBatisConstructorWithoutDatabase() throws Exception {
        materialize("targets", new TargetRow("project", "P-selected", "선택 프로젝트", "202612", "202801"),
            List.of("targetType", "targetId", "targetName", "startMonth", "endMonth"), new Filter(null, null));
    }

    @Test void nullableOpportunityEndMonthUsesProductionConstructorWithoutDatabase() throws Exception {
        materialize("targets", new TargetRow("opportunity", "O-selected", "선택 기회", "202611", null),
            List.of("targetType", "targetId", "targetName", "startMonth", "endMonth"), new Filter(null, null));
    }

    @Test void needProjectionPreservesEveryNonDefaultValueWithoutDatabase() throws Exception {
        materialize("needs", new NeedRow("O-selected", 17, "BE", "백엔드", 9, "202701"),
            List.of("opportunityId", "needSn", "jobCode", "jobName", "requiredCount", "startMonth"),
            Map.of("opportunityIds", List.of("O-selected")));
    }

    private void materialize(String operation, Record expected, List<String> labels, Object parameter) throws Exception {
        var statement = configuration().getMappedStatement(NS + operation);
        var components = expected.getClass().getRecordComponents();
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(labels.size());
        assertThat(statement.getResultMaps().get(0).getConstructorResultMappings())
            .extracting(mapping -> mapping.getColumn()).containsExactlyElementsOf(labels);
        for (int i = 0; i < labels.size(); i++) {
            metadata.setColumnName(i + 1, labels.get(i));
            metadata.setColumnLabel(i + 1, labels.get(i));
            metadata.setColumnType(i + 1, components[i].getType() == int.class ? Types.INTEGER : Types.VARCHAR);
        }
        try (var rows = RowSetProvider.newFactory().createCachedRowSet()) {
            rows.setMetaData(metadata);
            rows.moveToInsertRow();
            for (int i = 0; i < labels.size(); i++) {
                Object value = components[i].getAccessor().invoke(expected);
                if (value == null) rows.updateNull(i + 1);
                else rows.updateObject(i + 1, value);
            }
            rows.insertRow();
            rows.moveToCurrentRow();
            rows.beforeFirst();
            ResultSet jdbcRows = mock(ResultSet.class, org.mockito.AdditionalAnswers.delegatesTo(rows));
            doReturn(false).when(jdbcRows).isClosed();
            Statement jdbc = mock(Statement.class);
            when(jdbc.getResultSet()).thenReturn(jdbcRows);
            when(jdbc.getUpdateCount()).thenReturn(-1);
            var handler = new DefaultResultSetHandler(null, statement, null, null,
                statement.getBoundSql(parameter), RowBounds.DEFAULT);
            assertThat(handler.handleResultSets(jdbc)).containsExactly(expected);
        }
    }

    @Test void optionalFiltersOnlyBindPresentValuesAndMapperIsReadOnlyAndRegistered() throws Exception {
        var config = configuration();
        var targets = config.getMappedStatement(NS + "targets");
        assertThat(targets.getBoundSql(new Filter(null, null)).getParameterMappings()).isEmpty();
        assertThat(targets.getBoundSql(new Filter("project", null)).getParameterMappings())
            .extracting(mapping -> mapping.getProperty()).containsExactly("targetType");
        assertThat(targets.getBoundSql(new Filter(null, "chosen")).getParameterMappings())
            .extracting(mapping -> mapping.getProperty()).containsExactly("targetId");
        assertThat(targets.getBoundSql(new Filter("opportunity", "chosen")).getParameterMappings())
            .extracting(mapping -> mapping.getProperty()).containsExactly("targetType", "targetId");
        assertThat(AiRecommendationConfiguration.class.getAnnotation(MapperScan.class).basePackages())
            .contains(AiRecommendationOptionsMapper.class.getPackageName());
        for (var method : AiRecommendationOptionsMapper.class.getDeclaredMethods()) {
            assertThat(config.getMappedStatement(NS + method.getName()).getSqlCommandType())
                .isEqualTo(SqlCommandType.SELECT);
        }
    }
}
