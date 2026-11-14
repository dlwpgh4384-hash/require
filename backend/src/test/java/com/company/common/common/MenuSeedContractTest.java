package com.company.common.schema;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class MenuSeedContractTest {
    private static String seed() throws Exception {
        return new ClassPathResource("db/migration/V2__navigation_menu_seed.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void onlyTheSixApprovedScreensStartPcOnly() throws Exception {
        var rows = Pattern.compile("'(UI-[0-9]{2})', 2, [0-9]+, 'Y', 'Y'").matcher(seed());
        assertThat(rows.results().map(match -> match.group(1)).toList())
            .containsExactlyInAnyOrder("UI-10", "UI-14", "UI-27", "UI-23", "UI-24", "UI-25");
    }

    @Test
    void systemGroupAndItsFourScreensKeepTheirDatabaseLockSource() throws Exception {
        String sql = seed();
        var locked = Pattern.compile("\\('([GM][0-9]{3})', [^\\r\\n]+, 'Y', 'SYSTEM'\\)").matcher(sql);
        assertThat(locked.results().map(match -> match.group(1)).toList())
            .containsExactly("G010", "M027", "M028", "M029", "M030");
        assertThat(sql).contains("'M029', 'G010', '메뉴', 'UI-25'");
    }

    @Test
    void preparationDoesNotReseedAnEditedTreeOrIntroduceAnotherPhysicalTable() throws Exception {
        String sql = seed();
        assertThat(sql).contains("IF NOT EXISTS (SELECT 1 FROM TB_SY_MENU) THEN",
            "LOCK TABLE TB_SY_MENU IN EXCLUSIVE MODE");
        assertThat(sql).doesNotContain("UPDATE ", "DELETE ", "TRUNCATE ", "CREATE TABLE", "ON CONFLICT");
        var ids = Pattern.compile("\\('([GM][0-9]{3})',").matcher(sql)
            .results().map(match -> match.group(1)).toList();
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids.stream().filter(id -> id.startsWith("G")).toList()).hasSize(10);
        assertThat(ids.stream().filter(id -> id.startsWith("M")).toList()).hasSize(30);
    }
}
