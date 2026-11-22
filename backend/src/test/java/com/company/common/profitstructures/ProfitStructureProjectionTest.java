package com.company.common.profitstructures;

import com.company.common.profitstructures.config.ProfitStructureConfiguration;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Query;
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

class ProfitStructureProjectionTest {
    Configuration config() throws Exception {
        var config=new Configuration(); config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        for(String resource:List.of("mapper/personalcostprofits/PersonalCostProfitMapper.xml","mapper/profitstructures/ProfitStructureMapper.xml"))
            try(var input=new ClassPathResource(resource).getInputStream()) { new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse(); }
        return config;
    }
    @Test void scanAndStatementsMatchInterfaceAndOnlyWriteIsDownloadLog() throws Exception {
        assertThat(ProfitStructureConfiguration.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class).basePackages()).contains(ProfitStructureMapper.class.getPackageName());
        var config=config();
        for(var method:ProfitStructureMapper.class.getDeclaredMethods()) {
            var statement=config.getMappedStatement(ProfitStructureMapper.class.getName()+"."+method.getName());
            assertThat(statement.getSqlCommandType()).isEqualTo(method.getName().equals("insertAccess") ? org.apache.ibatis.mapping.SqlCommandType.INSERT : org.apache.ibatis.mapping.SqlCommandType.SELECT);
        }
    }
    @ParameterizedTest @ValueSource(strings={"snapshots","projects","costs","amounts","people","payroll","assignments","leaves","codes","payrollClasses"})
    void realMyBatisConstructorsPreserveEverySelectedLabelWithoutDatabase(String operation) throws Exception {
        var config=config(); var statement=config.getMappedStatement(ProfitStructureMapper.class.getName()+"."+operation);
        var mapping=statement.getResultMaps().get(0); var fields=mapping.getConstructorResultMappings();
        assertThat(fields).hasSize(mapping.getType().getRecordComponents().length);
        var sql=statement.getBoundSql(new Query(2026,2)).getSql();
        var metadata=new RowSetMetaDataImpl(); metadata.setColumnCount(fields.size()); var expected=new ArrayList<Object>();
        for(int i=0;i<fields.size();i++) {
            var field=fields.get(i); var type=field.getJavaType();
            assertThat(field.getColumn()).isEqualTo(mapping.getType().getRecordComponents()[i].getName());
            assertThat(sql).contains("\""+field.getColumn()+"\"");
            metadata.setColumnName(i+1,field.getColumn()); metadata.setColumnLabel(i+1,field.getColumn());
            metadata.setColumnType(i+1,type==BigDecimal.class ? Types.NUMERIC : type==LocalDate.class ? Types.DATE : type==Instant.class ? Types.TIMESTAMP : Types.VARCHAR);
            expected.add(type==BigDecimal.class ? new BigDecimal((i+101)+".25") : type==LocalDate.class ? LocalDate.of(2026,2,i%20+1) : type==Instant.class ? Instant.parse("2026-02-01T00:00:00Z") : "값-"+field.getColumn());
        }
        try(var rows=RowSetProvider.newFactory().createCachedRowSet()) {
            rows.setMetaData(metadata); rows.moveToInsertRow();
            for(int i=0;i<expected.size();i++) {
                var value=expected.get(i);
                if(value instanceof LocalDate day)rows.updateDate(i+1,java.sql.Date.valueOf(day));
                else if(value instanceof Instant time)rows.updateTimestamp(i+1,Timestamp.from(time)); else rows.updateObject(i+1,value);
            }
            rows.insertRow();rows.moveToCurrentRow();rows.beforeFirst();
            ResultSet jdbcRows=mock(ResultSet.class,org.mockito.AdditionalAnswers.delegatesTo(rows)); doReturn(false).when(jdbcRows).isClosed();
            doAnswer(call->{var day=rows.getDate((String)call.getArgument(0));return day==null?null:day.toLocalDate();}).when(jdbcRows).getObject(anyString(),eq(LocalDate.class));
            Statement jdbc=mock(Statement.class);when(jdbc.getResultSet()).thenReturn(jdbcRows);when(jdbc.getUpdateCount()).thenReturn(-1);
            var handler=new DefaultResultSetHandler(null,statement,null,null,statement.getBoundSql(new Query(2026,2)),RowBounds.DEFAULT);
            List<Object> actual=handler.handleResultSets(jdbc);assertThat(actual).hasSize(1);
            var parameterTypes = fields.stream().map(field -> field.getJavaType()).toArray(Class<?>[]::new);
            assertThat(actual.get(0)).isEqualTo(mapping.getType().getDeclaredConstructor(parameterTypes).newInstance(expected.toArray()));
        }
    }
    @Test void billedTurnkeyAndEffectiveAcquisitionPartAreIndependentOfDistributionAndEmployee() throws Exception {
        var config=config();String ns=ProfitStructureMapper.class.getName()+".";
        var sql=config.getMappedStatement(ns+"projects").getBoundSql(new Query(2026,2)).getSql();
        assertThat(sql).contains("TB_BL_BILL","SPLY_AMT","BILL_DT","CNCL_YN='N'","TB_PJ_PROJ_PART","APLY_BGN_YM","TB_PJ_TK_DSTRB").doesNotContain("TB_HR_DEV","RCPT_SUM_AMT");
        assertThat(config.getMappedStatement(ns+"snapshots").getBoundSql(new Query(2026,2)).getSql()).doesNotContain("COALESCE");
        assertThat(config.getMappedStatement(ns+"amounts").getBoundSql(new Query(2026,2)).getSql()).contains("sum(ADJ_AMT)").doesNotContain("-ADJ_AMT");
        assertThat(config.getMappedStatement(ns+"insertAccess").getBoundSql(Map.of("userId","admin","target","기간")).getSql()).contains("'UI-07'","'DNLD'","clock_timestamp()");
    }
}
