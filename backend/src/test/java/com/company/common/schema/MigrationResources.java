package com.company.common.schema;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 시험에서 기대 개수를 숫자로 적지 않고 포장된 db/migration 파일(V{n}__*.sql)에서 읽는다.
 * 표·외래 키가 늘어도 시험을 고칠 필요가 없고, 각 시험의 이름 검사는 그대로 빠진 표를 잡는다.
 */
public final class MigrationResources {
    private static final Pattern VERSION = Pattern.compile("V([0-9]+)__.*\\.sql");
    private static final Pattern TABLE = Pattern.compile(
        "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)\\s*\\(");
    private static final Pattern FOREIGN_KEY = Pattern.compile("(?i)\\b(FK_TB_[A-Z0-9_]+)");

    private MigrationResources() {}

    /** 버전 순서대로 읽은 마이그레이션 파일 내용. */
    public static List<String> scripts() {
        try {
            var resources = new ArrayList<>(List.of(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*__*.sql")));
            resources.sort(Comparator.comparingInt(r -> version(r.getFilename())));
            var scripts = new ArrayList<String>();
            for (var resource : resources) scripts.add(resource.getContentAsString(StandardCharsets.UTF_8));
            if (scripts.isEmpty()) throw new IllegalStateException("db/migration 파일이 없습니다.");
            return scripts;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 마이그레이션이 만드는 물리 표 이름(대문자, 나오는 순서). */
    public static List<String> tableNames() {
        var names = new ArrayList<String>();
        for (String sql : scripts()) {
            Matcher m = TABLE.matcher(withoutComments(sql));
            while (m.find()) names.add(m.group(1).toUpperCase(Locale.ROOT));
        }
        return names;
    }

    public static int tableCount() { return tableNames().size(); }

    /** 마이그레이션이 만드는 외래 키 이름(주석에만 적힌 것은 빼고, 대문자, 중복 없이). */
    public static List<String> foreignKeyNames() {
        var names = new LinkedHashSet<String>();
        for (String sql : scripts()) {
            Matcher m = FOREIGN_KEY.matcher(withoutComments(sql));
            while (m.find()) names.add(m.group(1).toUpperCase(Locale.ROOT));
        }
        return List.copyOf(names);
    }

    public static int foreignKeyCount() { return foreignKeyNames().size(); }

    private static String withoutComments(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
    }

    private static int version(String name) {
        Matcher m = VERSION.matcher(name == null ? "" : name);
        if (!m.matches()) throw new IllegalStateException("마이그레이션 파일 이름을 확인해 주세요: " + name);
        return Integer.parseInt(m.group(1));
    }
}
