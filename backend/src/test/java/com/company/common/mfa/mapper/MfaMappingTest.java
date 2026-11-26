package com.company.common.mfa.mapper;

import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class MfaMappingTest {
    @Test void explicitResultLabelsAndTypesMatchEveryAccountFieldWithoutDatabase() throws Exception {
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/MfaMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement(MfaMapper.class.getName() + ".lockAccount");
        var result = statement.getResultMaps().get(0);
        String sql = statement.getBoundSql(Map.of("userId", "mfa-admin")).getSql().toLowerCase();
        var row = Map.<String, Object>of("user_id", "mfa-admin", "email_addr", "actor@example.invalid",
            "user_stat_cd", "USE", "mfa_code_hash", "non-default-hash", "mfa_expr_dttm", Instant.parse("2026-01-02T03:04:05Z"),
            "mfa_err_cnt", 3, "temp_pwd_yn", "Y", "administrator", true);
        var account = new MfaRows.Account();
        var target = configuration.newMetaObject(account);
        assertThat(result.getResultMappings()).hasSize(MfaRows.Account.class.getFields().length);
        for (var mapping : result.getResultMappings()) {
            assertThat(sql).contains(mapping.getColumn());
            assertThat(row).containsKey(mapping.getColumn());
            assertThat(mapping.getJavaType()).isEqualTo(MfaRows.Account.class.getField(mapping.getProperty()).getType());
            assertThat(mapping.getTypeHandler()).isNotNull();
            target.setValue(mapping.getProperty(), row.get(mapping.getColumn()));
            assertThat(target.getValue(mapping.getProperty())).isEqualTo(row.get(mapping.getColumn()));
        }
        assertThat(account.userId).isEqualTo("mfa-admin");
        assertThat(account.email).isEqualTo("actor@example.invalid");
        assertThat(account.status).isEqualTo("USE");
        assertThat(account.codeHash).isEqualTo("non-default-hash");
        assertThat(account.expiresAt).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"));
        assertThat(account.errors).isEqualTo(3);
        assertThat(account.temporary).isEqualTo("Y");
        assertThat(account.administrator).isTrue();
        assertThat(sql).contains("ur.role_id", "r.role_id", "for update");
    }
}
