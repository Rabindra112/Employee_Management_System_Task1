package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;

public class V3__role_management_lock extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS role_management_lock (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB");
        }
    }
}
