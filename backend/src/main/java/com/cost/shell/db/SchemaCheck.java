package com.cost.shell.db;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

@Component
public class SchemaCheck implements ApplicationRunner {
    private final DataSource dataSource;

    public SchemaCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        try (var connection = dataSource.getConnection()) {
            MenuSchemaInspector.inspect(connection);
        }
    }
}
