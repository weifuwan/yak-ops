package io.yak.ops.tools.datasource;

import io.yak.ops.common.sql.SqlScriptRunner;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.annotation.DbType;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SchemaDao {

    private final DataSource dataSource;

    private final DbType dbType;

    private static final String[] MODELING_TABLES = {
            "t_yak_ops_business_type",
            "t_yak_ops_business_type_related_data_domain",
            "t_yak_ops_dw_layer",
            "t_yak_ops_data_domain",
            "t_yak_ops_business_process",
            "t_yak_ops_data_mart",
            "t_yak_ops_subject_domain",
            "t_yak_ops_dimension_table",
            "t_yak_ops_dimension_table_fields",
            "t_yak_ops_standard_directory",
            "t_yak_ops_field_standard",
            "t_yak_ops_code_standard",
            "t_yak_ops_code_detail",
            "t_yak_ops_unit_standard",
            "t_yak_ops_dictionary_standard",
            "t_yak_ops_indicator_atom",
            "t_yak_ops_indicator_derivative",
            "t_yak_ops_indicator_compose",
            "t_yak_ops_indicator_modifier",
            "t_yak_ops_time_period_category",
            "t_yak_ops_time_period",
            "t_yak_ops_version_management"
    };

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
        String db = dbType.getDb();
        if (!"mysql".equalsIgnoreCase(db)
                && !"postgresql".equalsIgnoreCase(db)
                && !"h2".equalsIgnoreCase(db)) {
            throw new IllegalStateException("Modeling schema is not available for database type " + db);
        }

        migrateLegacyModelingTableNames();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String tableName : MODELING_TABLES) {
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

        String sqlFilePath = String.format("sql/modeling_%s.sql", db);
        try {
            new SqlScriptRunner(dataSource, sqlFilePath).execute();
            log.info("Ensured data modeling schema using {}", sqlFilePath);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize data modeling schema using " + sqlFilePath, ex);
        }
    }

    /**
     * Rename the former cockpit-prefixed modeling tables in place.
     *
     * <p>The operation preserves existing rows and indexes. If both the legacy and
     * normalized table exist, initialization stops instead of guessing which data
     * set should be kept or merged.
     */
    private void migrateLegacyModelingTableNames() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String tableName : MODELING_TABLES) {
                String legacyTableName = tableName.replace("t_yak_ops_", "t_cockpit_");
                boolean legacyExists = tableExists(metadata, connection, legacyTableName);
                boolean currentExists = tableExists(metadata, connection, tableName);

                if (legacyExists && currentExists) {
                    throw new IllegalStateException(
                            "Both legacy modeling table " + legacyTableName + " and normalized table "
                                    + tableName + " exist. Back up and reconcile the data before continuing.");
                }
                if (legacyExists) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE " + quoteIdentifier(legacyTableName)
                                + " RENAME TO " + quoteIdentifier(tableName));
                    }
                    log.info("Renamed modeling table {} to {}", legacyTableName, tableName);
                    currentExists = true;
                }

                // Also retry index normalization for a table renamed by an earlier partial startup.
                if (currentExists) {
                    renameModelingIndexes(metadata, connection, tableName);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to migrate legacy modeling table names", ex);
        }
    }

    private void renameModelingIndexes(DatabaseMetaData metadata, Connection connection, String tableName)
            throws SQLException {
        Set<String> existingIndexes = new HashSet<>();
        java.util.Map<String, String> actualIndexNames = new java.util.LinkedHashMap<>();
        readIndexNames(metadata, connection, tableName, existingIndexes, actualIndexNames);
        if (actualIndexNames.isEmpty()) {
            readIndexNames(metadata, connection, tableName.toUpperCase(Locale.ROOT),
                    existingIndexes, actualIndexNames);
        }

        for (String normalizedSourceName : new HashSet<>(actualIndexNames.keySet())) {
            String normalizedName = normalizeModelingIndexName(normalizedSourceName);
            if (normalizedName.equals(normalizedSourceName) || existingIndexes.contains(normalizedName)) {
                continue;
            }

            String actualSourceName = actualIndexNames.get(normalizedSourceName);
            String sql;
            if ("mysql".equalsIgnoreCase(dbType.getDb())) {
                sql = "ALTER TABLE " + quoteIdentifier(tableName) + " RENAME INDEX "
                        + quoteIdentifier(actualSourceName) + " TO " + quoteIdentifier(normalizedName);
            } else {
                sql = "ALTER INDEX " + quoteIdentifier(actualSourceName) + " RENAME TO "
                        + quoteIdentifier(normalizedName);
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
            existingIndexes.add(normalizedName);
        }
    }

    private void readIndexNames(
            DatabaseMetaData metadata,
            Connection connection,
            String tableName,
            Set<String> existingIndexes,
            java.util.Map<String, String> actualIndexNames) throws SQLException {
        try (ResultSet indexes = metadata.getIndexInfo(
                connection.getCatalog(), connection.getSchema(), tableName, false, false)) {
            while (indexes.next()) {
                String actualName = indexes.getString("INDEX_NAME");
                if (actualName == null) {
                    continue;
                }
                String normalizedName = actualName.toLowerCase(Locale.ROOT);
                existingIndexes.add(normalizedName);
                actualIndexNames.put(normalizedName, actualName);
            }
        }
    }

    private String normalizeModelingIndexName(String indexName) {
        if (indexName.startsWith("idx_t_cockpit_")) {
            return "idx_yak_" + indexName.substring("idx_t_cockpit_".length());
        }
        if (indexName.startsWith("idx_")) {
            return "idx_yak_" + indexName.substring("idx_".length());
        }
        if (indexName.startsWith("uk_")) {
            return "uk_yak_" + indexName.substring("uk_".length());
        }
        return indexName;
    }

    private String quoteIdentifier(String identifier) {
        return "mysql".equalsIgnoreCase(dbType.getDb())
                ? "`" + identifier + "`"
                : identifier;
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
