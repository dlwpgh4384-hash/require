package com.company.common.commonops;

import com.company.common.commonops.mapper.AccountAccessMapper;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AccountAccessMapperTest {
    Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        String path = "mapper/AccountAccessMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
    @Test void explicitAccountMappingMaterializesEveryNonDefaultFieldWithoutDatabase() throws Exception {
        var configuration = configuration();
        var now = Instant.parse("2026-01-02T03:04:05Z");
        var row = Map.<String, Object>ofEntries(Map.entry("user_id", "account-a"), Map.entry("user_nm", "이름"),
            Map.entry("email_addr", "a@example.invalid"), Map.entry("pwd_hash", "redacted-test-hash"),
            Map.entry("user_stat_cd", "LOCK"), Map.entry("temp_pwd_yn", "Y"), Map.entry("modified_at", now),
            Map.entry("pwd_chg_dttm", now.minusSeconds(2)), Map.entry("last_login_dttm", now.minusSeconds(1)), Map.entry("pwd_err_cnt", 4));
        var account = new AccountAccessMapper.Account();
        var target = configuration.newMetaObject(account);
        for (var mapping : configuration.getResultMap(AccountAccessMapper.class.getName() + ".account").getResultMappings()) {
            assertThat(row).containsKey(mapping.getColumn());
            target.setValue(mapping.getProperty(), row.get(mapping.getColumn()));
        }
        assertThat(account.userId).isEqualTo("account-a"); assertThat(account.name).isEqualTo("이름");
        assertThat(account.email).isEqualTo("a@example.invalid"); assertThat(account.hash).isEqualTo("redacted-test-hash");
        assertThat(account.status).isEqualTo("LOCK"); assertThat(account.temporary).isEqualTo("Y");
        assertThat(account.modifiedAt).isEqualTo(now); assertThat(account.passwordChangedAt).isEqualTo(now.minusSeconds(2));
        assertThat(account.lastLoginAt).isEqualTo(now.minusSeconds(1)); assertThat(account.failures).isEqualTo(4);
    }
    @Test void explicitPermissionMappingContainsAllSevenActionsAndDeviceCapability() throws Exception {
        var configuration = configuration();
        var values = Map.ofEntries(Map.entry("menu_id", "M001"), Map.entry("scrn_id", "UI-16"), Map.entry("pc_only_yn", "Y"),
            Map.entry("inq_yn", "Y"), Map.entry("reg_yn", "Y"), Map.entry("upd_yn", "Y"), Map.entry("del_yn", "Y"),
            Map.entry("upld_yn", "Y"), Map.entry("dnld_yn", "Y"), Map.entry("cnfm_yn", "Y"));
        var permission = new AccountAccessMapper.Permission(); var target = configuration.newMetaObject(permission);
        for (var mapping : configuration.getResultMap(AccountAccessMapper.class.getName() + ".permission").getResultMappings())
            target.setValue(mapping.getProperty(), values.get(mapping.getColumn()));
        assertThat(permission.menuId).isEqualTo("M001"); assertThat(permission.screenId).isEqualTo("UI-16");
        assertThat(permission.pcOnlyYn).isEqualTo("Y");
        assertThat(java.util.List.of(permission.readYn, permission.createYn, permission.updateYn, permission.deleteYn,
            permission.uploadYn, permission.downloadYn, permission.cnfmYn)).containsOnly("Y");
    }
}
