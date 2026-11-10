package db.migration;

import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class V2__Initial_menu extends BaseJavaMigration {
    public interface SeedMapper {
        @Update("LOCK TABLE TB_SY_MENU IN EXCLUSIVE MODE")
        void lock();

        @Select("SELECT COUNT(*) FROM TB_SY_MENU")
        long count();

        @InsertProvider(type = SeedSql.class, method = "insert")
        int insert();
    }

    public static class SeedSql {
        public static String insert() throws Exception {
            return new ClassPathResource("db/menu-seed.sql").getContentAsString(StandardCharsets.UTF_8);
        }
    }

    @Override
    public void migrate(Context context) throws Exception {
        var transactionFactory = new ManagedTransactionFactory();
        var properties = new Properties();
        properties.setProperty("closeConnection", "false");
        transactionFactory.setProperties(properties);
        var configuration = new Configuration();
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("flyway", transactionFactory,
            new org.springframework.jdbc.datasource.SingleConnectionDataSource(context.getConnection(), true)));
        configuration.addMapper(SeedMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(context.getConnection())) {
            var mapper = session.getMapper(SeedMapper.class);
            mapper.lock();
            if (mapper.count() == 0) {
                mapper.insert();
            }
        }
        // Flyway owns commit/rollback and connection lifetime, including the schema history write.
    }
}
