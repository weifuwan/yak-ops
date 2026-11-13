package io.yak.ops.tools.datasource;

import io.yak.ops.common.sql.SqlScriptRunner;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.annotation.DbType;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SchemaDao {

    private final DataSource dataSource;

    private final DbType dbType;

    public SchemaDao(DataSource dataSource, DbType dbType) {
        this.dataSource = dataSource;
        this.dbType = dbType;
    }

    /**
     * The baseline SQL contains DROP TABLE statements. Only run it in an empty schema.
     */
    public boolean hasExistingTables() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (
                    ResultSet tables = metadata.getTables(
                            connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
                return tables.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to inspect existing database tables", ex);
        }
    }

    /**
     * Check for old DolphinScheduler tables before interpreting the schema as Yak Ops.
     */
    public boolean hasLegacySchemaTables() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (
                    ResultSet tables = metadata.getTables(
                            connection.getCatalog(), connection.getSchema(), "%", null)) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (tableName == null) {
                        continue;
                    }
                    String normalizedName = tableName.toLowerCase(Locale.ROOT);
                    if (normalizedName.startsWith("t_ds_")
                            || normalizedName.startsWith("t_escheduler_")) {
                        return true;
                    }
                }
                return false;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to inspect legacy database tables", ex);
        }
    }

    /**
     * Execute the complete baseline schema on an empty database only.
     * Call through YakOpsManager to enforce the safety checks.
     */
    public void initSchema() {
        String sqlFilePath = String.format("sql/yak-ops_%s.sql", dbType.getDb());
        try {
            new SqlScriptRunner(dataSource, sqlFilePath).execute();
            log.info("Initialized database using {}", sqlFilePath);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize database using " + sqlFilePath, ex);
        }
    }

    /**
     * Add only the user preference table, without executing the destructive baseline SQL.
     * The script uses CREATE TABLE IF NOT EXISTS and is safe to repeat.
     */
    public void ensureUserPreferenceSchema() {
        String sqlFilePath = String.format("sql/patches/user-preference_%s.sql", dbType.getDb());
        try {
            new SqlScriptRunner(dataSource, sqlFilePath).execute();
            log.info("Ensured user preference schema using {}", sqlFilePath);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize user preferences using " + sqlFilePath, ex);
        }
    }


    /**
     * Ensure the additive data modeling schema after validating that no legacy
     * unscoped modeling tables would be reused accidentally.
     */
    public void ensureModelingSchema() {
        String[] modelingTables = {
                "t_cockpit_business_type",
                "t_cockpit_dw_layer",
                "t_cockpit_data_domain",
                "t_cockpit_business_process",
                "t_cockpit_data_mart",
                "t_cockpit_subject_domain",
                "t_cockpit_business_type_related_data_domain"
        };
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String tableName : modelingTables) {
                if (tableExists(metadata, connection, tableName)
                        && !columnExists(metadata, connection, tableName, "project_code")) {
                    throw new IllegalStateException(
                            "Legacy modeling table " + tableName
                                    + " has no project_code column. Refusing to attach unscoped rows "
                                    + "to a Yak Ops workspace; migrate those rows explicitly first.");
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to validate modeling schema", ex);
        }

        String db = dbType.getDb();
        if (!"mysql".equalsIgnoreCase(db)
                && !"postgresql".equalsIgnoreCase(db)
                && !"h2".equalsIgnoreCase(db)) {
            throw new IllegalStateException("Modeling schema is not available for database type " + db);
        }
        String sqlFilePath = String.format("sql/modeling_%s.sql", db);
        try {
            new SqlScriptRunner(dataSource, sqlFilePath).execute();
            log.info("Ensured data modeling schema using {}", sqlFilePath);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize data modeling schema using " + sqlFilePath, ex);
        }
    }

    private boolean tableExists(DatabaseMetaData metadata, Connection connection, String tableName)
            throws SQLException {
        try (ResultSet tables = metadata.getTables(
                connection.getCatalog(), connection.getSchema(), tableName, new String[]{"TABLE"})) {
            if (tables.next()) {
                return true;
            }
        }
        try (ResultSet tables = metadata.getTables(
                connection.getCatalog(), connection.getSchema(), tableName.toUpperCase(Locale.ROOT),
                new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private boolean columnExists(DatabaseMetaData metadata, Connection connection, String tableName, String columnName)
            throws SQLException {
        try (ResultSet columns = metadata.getColumns(
                connection.getCatalog(), connection.getSchema(), tableName, columnName)) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = metadata.getColumns(
                connection.getCatalog(), connection.getSchema(), tableName.toUpperCase(Locale.ROOT),
                columnName.toUpperCase(Locale.ROOT))) {
            return columns.next();
        }
    }

    public String getCurrentVersion() {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT version FROM t_yak_ops_version");
                ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getString(1) : null;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read t_yak_ops_version", ex);
        }
    }
}
