package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import com.cost.shell.db.MenuSchemaInspector;

public class V1__Menu_schema extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (var statement = context.getConnection().prepareStatement("SELECT to_regclass('tb_sy_menu')")) {
            try (var result = statement.executeQuery()) {
                if (result.next() && result.getString(1) != null) {
                    MenuSchemaInspector.inspect(context.getConnection());
                    return; // Existing structure, comments, constraints and rows stay untouched.
                }
            }
        }
        ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource("db/schema.sql"));
        MenuSchemaInspector.inspect(context.getConnection());
    }
}
