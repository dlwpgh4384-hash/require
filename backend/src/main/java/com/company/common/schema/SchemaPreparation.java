package com.company.common.schema;

import java.nio.charset.StandardCharsets;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class SchemaPreparation {
    private final SqlSessionFactory sessions;

    public SchemaPreparation(SqlSessionFactory sessions) {
        this.sessions = sessions;
    }

    public void prepare() throws Exception {
        try (var session = sessions.openSession(false)) {
            try {
                for (String resource : new String[]{"db/migration/V1__navigation_schema.sql", "db/migration/V2__navigation_menu_seed.sql",
                        "db/migration/V3__common_function_seed.sql", "db/migration/V4__user_management_seed.sql"}) {
                    String sql = new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
                    try (var statement = session.getConnection().createStatement()) {
                        statement.execute(sql);
                    }
                }
                session.commit(true);
            } catch (Exception exception) {
                session.rollback(true);
                throw exception;
            }
        }
    }

    public static String diagnostic(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        if (current instanceof java.sql.SQLException sql && "P0001".equals(sql.getSQLState())) {
            return sql.getMessage();
        }
        return "Database preparation failed; no preparation transaction was committed. Check database configuration and availability.";
    }
}
