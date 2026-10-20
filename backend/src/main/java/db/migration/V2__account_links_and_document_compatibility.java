package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Add account ownership and preserve legacy document columns/data during migration. */
public class V2__account_links_and_document_compatibility extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!hasColumn(connection, "app_users", "employee_id")) {
            execute(connection, "ALTER TABLE app_users ADD COLUMN employee_id BIGINT NULL");
        }
        if (!hasConstraint(connection, "UNIQUE", "employee_id")) {
            execute(connection, "ALTER TABLE app_users ADD CONSTRAINT uk_account_employee UNIQUE (employee_id)");
        }
        if (!hasConstraint(connection, "FOREIGN KEY", "employee_id")) {
            execute(connection, "ALTER TABLE app_users ADD CONSTRAINT fk_account_employee "
                    + "FOREIGN KEY (employee_id) REFERENCES employees(id)");
        }
        execute(connection, "UPDATE app_users SET role='EMPLOYEE' WHERE role='USER'");

        Map<String, String[]> legacyColumns = new LinkedHashMap<>();
        legacyColumns.put("created_at", new String[]{"uploaded_at", "DATETIME(6)"});
        legacyColumns.put("created_by", new String[]{"uploaded_by", "VARCHAR(255)"});
        legacyColumns.put("size", new String[]{"file_size", "BIGINT"});
        legacyColumns.put("storage_key", new String[]{"file_path", "VARCHAR(255)"});
        for (var entry : legacyColumns.entrySet()) {
            String legacy = entry.getKey();
            String canonical = entry.getValue()[0];
            if (!hasColumn(connection, "document_versions", canonical)) {
                execute(connection, "ALTER TABLE document_versions ADD COLUMN `" + canonical + "` "
                        + entry.getValue()[1] + " NULL");
            }
            if (hasColumn(connection, "document_versions", legacy)) {
                execute(connection, "UPDATE document_versions SET `" + canonical + "`=`" + legacy
                        + "` WHERE `" + canonical + "` IS NULL");
                // Retain old data and indexes; current inserts no longer need to supply duplicate legacy fields.
                String legacyType = columnType(connection, legacy);
                execute(connection, "ALTER TABLE document_versions MODIFY COLUMN `" + legacy + "` "
                        + legacyType + " NULL");
            }
            execute(connection, "ALTER TABLE document_versions MODIFY COLUMN `" + canonical + "` "
                    + entry.getValue()[1] + " NOT NULL");
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name=? AND column_name=?")) {
            query.setString(1, table);
            query.setString(2, column);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getInt(1) > 0; }
        }
    }

    private boolean hasConstraint(Connection connection, String type, String column) throws SQLException {
        try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.table_constraints t "
                + "JOIN information_schema.key_column_usage k ON t.constraint_schema=k.constraint_schema "
                + "AND t.table_name=k.table_name AND t.constraint_name=k.constraint_name "
                + "WHERE t.table_schema=DATABASE() AND t.table_name='app_users' "
                + "AND t.constraint_type=? AND k.column_name=?")) {
            query.setString(1, type);
            query.setString(2, column);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getInt(1) > 0; }
        }
    }

    private String columnType(Connection connection, String column) throws SQLException {
        try (var query = connection.prepareStatement("SELECT column_type FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='document_versions' AND column_name=?")) {
            query.setString(1, column);
            try (var rows = query.executeQuery()) {
                rows.next();
                String type = rows.getString(1);
                if (!type.matches("[a-zA-Z0-9(), ]+")) throw new SQLException("Unexpected legacy column type");
                return type;
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
