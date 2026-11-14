package com.company.common.developers.mapper;

import java.math.BigDecimal;
import java.sql.*;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class DeveloperSequenceTypeHandler extends BaseTypeHandler<Integer> {
    @Override public void setNonNullParameter(PreparedStatement s, int i, Integer value, JdbcType type) throws SQLException {
        s.setBigDecimal(i, BigDecimal.valueOf(value));
    }
    private Integer exact(BigDecimal value) throws SQLException {
        try { return value == null ? null : value.intValueExact(); }
        catch (ArithmeticException e) { throw new SQLException("Invalid developer status sequence", e); }
    }
    @Override public Integer getNullableResult(ResultSet r, String column) throws SQLException { return exact(r.getBigDecimal(column)); }
    @Override public Integer getNullableResult(ResultSet r, int column) throws SQLException { return exact(r.getBigDecimal(column)); }
    @Override public Integer getNullableResult(CallableStatement s, int column) throws SQLException { return exact(s.getBigDecimal(column)); }
}
