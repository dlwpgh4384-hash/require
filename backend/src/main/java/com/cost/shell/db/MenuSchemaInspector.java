package com.cost.shell.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Reads PostgreSQL catalog metadata; never alters an existing table or constraint. */
public final class MenuSchemaInspector {
    private static final Logger LOG = LoggerFactory.getLogger(MenuSchemaInspector.class);
    private static final Set<String> REQUIRED = Set.of(
        "menu_id", "upper_menu_id", "menu_nm", "scrn_id", "menu_lvl", "sort_ord", "use_yn", "pc_only_yn", "icon_nm"
    );
    private record Column(String type, boolean required, String defaultFragment) {
    }
    private static final Map<String, Column> EXPECTED = Map.ofEntries(
        Map.entry("menu_id", new Column("character varying(20)", true, null)),
        Map.entry("upper_menu_id", new Column("character varying(20)", false, null)),
        Map.entry("menu_nm", new Column("character varying(50)", true, null)),
        Map.entry("scrn_id", new Column("character varying(10)", false, null)),
        Map.entry("menu_lvl", new Column("numeric(1,0)", true, null)),
        Map.entry("sort_ord", new Column("numeric(5,0)", true, null)),
        Map.entry("use_yn", new Column("character(1)", true, "'Y'")),
        Map.entry("pc_only_yn", new Column("character(1)", true, "'N'")),
        Map.entry("icon_nm", new Column("character varying(30)", false, null)),
        Map.entry("lock_yn", new Column("character(1)", true, "'N'")),
        Map.entry("reg_user_id", new Column("character varying(30)", true, null)),
        Map.entry("reg_dttm", new Column("timestamp without time zone", true, "CURRENT_TIMESTAMP")),
        Map.entry("mod_user_id", new Column("character varying(30)", false, null)),
        Map.entry("mod_dttm", new Column("timestamp without time zone", false, null))
    );

    private MenuSchemaInspector() {
    }

    public static List<String> inspect(Connection connection) throws SQLException {
        var actual = new HashMap<String, Column>();
        try (var query = connection.prepareStatement("""
            SELECT a.attname, format_type(a.atttypid, a.atttypmod), a.attnotnull,
                   pg_get_expr(d.adbin, d.adrelid)
            FROM pg_attribute a
            LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
            WHERE a.attrelid = to_regclass('tb_sy_menu') AND a.attnum > 0 AND NOT a.attisdropped
            """)) {
            try (var result = query.executeQuery()) {
                while (result.next()) {
                    actual.put(result.getString(1), new Column(result.getString(2), result.getBoolean(3), result.getString(4)));
                }
            }
        }
        var differences = new ArrayList<String>();
        EXPECTED.forEach((name, expected) -> {
            var found = actual.get(name);
            if (found == null) {
                differences.add(name + " expected=" + expected.type() + " actual=MISSING");
            } else {
                if (!expected.type().equals(found.type()) || expected.required() != found.required()) {
                    differences.add(name + " expected=" + expected.type() + "/notNull=" + expected.required()
                        + " actual=" + found.type() + "/notNull=" + found.required());
                }
                String wanted = expected.defaultFragment();
                String defaultValue = found.defaultFragment();
                if ((wanted == null && defaultValue != null)
                    || (wanted != null && (defaultValue == null || !defaultValue.contains(wanted)))) {
                    differences.add(name + " default differs from schema reference");
                }
            }
        });
        try (var query = connection.prepareStatement("""
            SELECT c.contype, c.conname, pg_get_constraintdef(c.oid)
            FROM pg_constraint c WHERE c.conrelid = to_regclass('tb_sy_menu')
            """)) {
            var checks = new HashMap<String, String>();
            boolean primaryKey = false;
            try (var result = query.executeQuery()) {
                while (result.next()) {
                    if ("p".equals(result.getString(1))) {
                        primaryKey = "PRIMARY KEY (menu_id)".equals(result.getString(3));
                    }
                    if ("c".equals(result.getString(1))) {
                        checks.put(result.getString(2).toLowerCase(Locale.ROOT), result.getString(3));
                    }
                }
            }
            if (!primaryKey) differences.add("menu_id expected=single primary key actual=DIFFERENT_OR_MISSING");
            var flags = List.of("use_yn", "pc_only_yn", "lock_yn");
            for (int index = 0; index < flags.size(); index++) {
                String definition = checks.get("ck_tb_sy_menu_0" + (index + 1));
                if (definition == null || !definition.contains(flags.get(index))
                    || !definition.contains("'Y'") || !definition.contains("'N'")) {
                    differences.add(flags.get(index) + " expected=named Y/N CHECK actual=DIFFERENT_OR_MISSING");
                }
            }
        }
        for (String difference : differences) {
            LOG.warn("SCHEMA_DIFFERENCE TB_SY_MENU {} ; no automatic changes", difference);
        }
        if (!actual.keySet().containsAll(REQUIRED)) {
            throw new SQLException("TB_SY_MENU lacks required query columns; manual review required");
        }
        return List.copyOf(differences);
    }
}
