package com.company.common.session;

import com.company.common.session.mapper.SessionMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.BigDecimalTypeHandler;
import org.apache.ibatis.type.StringTypeHandler;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionMapperTest {
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        try (var input = new ClassPathResource("mapper/SessionMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "mapper/SessionMapper.xml", config.getSqlFragments()).parse();
        }
        return config;
    }

    @Test void effectiveMonthUsesPhysicalColumnsAndDecimalMaterialization() throws Exception {
        var config = configuration();
        var statement = config.getMappedStatement(SessionMapper.class.getName() + ".selectSessionMinutes");
        var sql = statement.getBoundSql(Map.of("month", "202601"));
        assertThat(sql.getSql()).contains("s.std_val", "TB_SY_OPER_STD", "s.std_cd = 'SESSION_MIN'",
            "s.aply_bgn_ym <= ?", "ORDER BY s.aply_bgn_ym DESC", "LIMIT 1");
        assertThat(sql.getParameterMappings()).extracting(mapping -> mapping.getProperty()).containsExactly("month");
        assertThat(statement.getResultMaps().get(0).getType()).isEqualTo(BigDecimal.class);
        var handler = config.getTypeHandlerRegistry().getTypeHandler(BigDecimal.class);
        assertThat(handler).isInstanceOf(BigDecimalTypeHandler.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getBigDecimal(1)).thenReturn(new BigDecimal("12.0000"));
        assertThat(handler.getResult(row, 1)).isEqualByComparingTo("12.0000");
    }

    @Test void accountScalarsHaveNoRoleOrInventedPasswordColumn() throws Exception {
        var config = configuration();
        for (String method : new String[] {"selectAccountStatus", "selectTemporaryPassword"}) {
            var statement = config.getMappedStatement(SessionMapper.class.getName() + "." + method);
            var sql = statement.getBoundSql(Map.of("userId", "reader"));
            assertThat(sql.getSql()).contains("TB_SY_USER", "u.user_id = ?");
            assertThat(statement.getResultMaps().get(0).getType()).isEqualTo(String.class);
        }
        var handler = config.getTypeHandlerRegistry().getTypeHandler(String.class);
        assertThat(handler).isInstanceOf(StringTypeHandler.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn("STOP");
        assertThat(handler.getResult(row, 1)).isEqualTo("STOP");
    }
}
