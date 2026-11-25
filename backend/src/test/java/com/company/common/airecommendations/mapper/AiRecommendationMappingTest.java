package com.company.common.airecommendations.mapper;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.rowset.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiRecommendationMappingTest {
    Configuration configuration() throws Exception {
        var config=new Configuration();config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource="mapper/airecommendations/AiRecommendationMapper.xml";
        try(var stream=new ClassPathResource(resource).getInputStream()) { new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse(); }
        return config;
    }
    @Test void everyMapperMethodIsRegistered() throws Exception {
        var config=configuration();
        for(var method:AiRecommendationMapper.class.getDeclaredMethods()) assertThat(config.hasStatement(AiRecommendationMapper.class.getName()+"."+method.getName())).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"target","person","evidence","payroll","price","areas","candidate"})
    void everyRecordMaterializesThroughRealMyBatisWithoutDatabase(String name) throws Exception {
        var config=configuration();var statement=config.getMappedStatement(AiRecommendationMapper.class.getName()+"."+name);
        var mapping=statement.getResultMaps().get(0);var args=mapping.getConstructorResultMappings();
        var metadata=new RowSetMetaDataImpl();metadata.setColumnCount(args.size());var values=new ArrayList<Object>();
        for(int i=0;i<args.size();i++) {
            var type=args.get(i).getJavaType();String label=args.get(i).getColumn();
            assertThat(label).isEqualTo(mapping.getType().getRecordComponents()[i].getName());
            metadata.setColumnName(i+1,label);metadata.setColumnLabel(i+1,label);
            metadata.setColumnType(i+1,type==BigDecimal.class?Types.NUMERIC:type==int.class||type==Integer.class?Types.INTEGER:type==boolean.class?Types.BOOLEAN:type==LocalDate.class?Types.DATE:type==Instant.class?Types.TIMESTAMP:Types.VARCHAR);
            values.add(type==BigDecimal.class?new BigDecimal("17.25"):type==int.class||type==Integer.class?17:type==boolean.class?true:type==LocalDate.class?LocalDate.of(2026,11,25):type==Instant.class?Instant.parse("2026-11-25T01:02:03Z"):"value-"+label);
        }
        try(var rows=RowSetProvider.newFactory().createCachedRowSet()) {
            rows.setMetaData(metadata);rows.moveToInsertRow();
            for(int i=0;i<values.size();i++) {Object value=values.get(i);if(value instanceof LocalDate d)rows.updateDate(i+1,java.sql.Date.valueOf(d));else if(value instanceof Instant t)rows.updateTimestamp(i+1,Timestamp.from(t));else rows.updateObject(i+1,value);}
            rows.insertRow();rows.moveToCurrentRow();rows.beforeFirst();
            ResultSet results=mock(ResultSet.class,org.mockito.AdditionalAnswers.delegatesTo(rows));doReturn(false).when(results).isClosed();
            doAnswer(c->rows.getDate((String)c.getArgument(0)).toLocalDate()).when(results).getObject(anyString(),eq(LocalDate.class));
            Statement jdbc=mock(Statement.class);when(jdbc.getResultSet()).thenReturn(results);when(jdbc.getUpdateCount()).thenReturn(-1);
            Map<String,Object> parameters=new HashMap<>();parameters.put("type","project");parameters.put("id","p");parameters.put("developerId","d");parameters.put("customerId","c");parameters.put("industryCode","IT");parameters.put("today",LocalDate.of(2026,11,25));parameters.put("firstDay",LocalDate.of(2026,12,1));parameters.put("month","202612");
            var actual=new DefaultResultSetHandler(null,statement,null,null,statement.getBoundSql(parameters),RowBounds.DEFAULT).handleResultSets(jdbc);
            assertThat(actual).containsExactly(mapping.getType().getDeclaredConstructors()[0].newInstance(values.toArray()));
        }
    }
}
