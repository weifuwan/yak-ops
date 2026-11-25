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
            "t_yak_ops_dimension_table_publish",
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
     * Cover the project and time columns of the operations overview state counts so the
     * aggregations can be served from the index instead of looking up every matching row.
     */
    public void ensureInstanceStateCountIndexes() {
        ensureInstanceStateCountIndex("t_yak_ops_task_instance",
                "idx_project_submit_time",
                "idx_project_submit_time_state",
                "(project_code, submit_time, state)");
        ensureInstanceStateCountIndex("t_yak_ops_workflow_instance",
                "idx_project_start_time",
                "idx_project_start_time_state",
                "(project_code, start_time, is_sub_workflow, state)");
    }

    private void ensureInstanceStateCountIndex(String tableName,
                                               String legacyIndexName,
                                               String indexName,
                                               String columns) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!tableExists(metadata, connection, tableName)) {
                return;
            }

            boolean indexPresent = indexExists(metadata, connection, tableName, indexName);
            boolean legacyIndexPresent = indexExists(metadata, connection, tableName, legacyIndexName);
            try (Statement statement = connection.createStatement()) {
                if (!indexPresent) {
                    statement.execute(
                            "CREATE INDEX " + indexName + " ON " + quoteIdentifier(tableName) + " " + columns);
                    log.info("Created index {} on {}", indexName, tableName);
                }
                if (legacyIndexPresent) {
                    statement.execute(dropIndexSql(tableName, legacyIndexName));
                    log.info("Dropped redundant index {} on {}", legacyIndexName, tableName);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to ensure instance state count index on " + tableName, ex);
        }
    }

    private String dropIndexSql(String tableName, String indexName) {
        if ("mysql".equalsIgnoreCase(dbType.getDb())) {
            return "ALTER TABLE " + quoteIdentifier(tableName) + " DROP INDEX " + indexName;
        }
        return "DROP INDEX " + indexName;
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

        ensureDimensionShortNameColumn();

        String sqlFilePath = String.format("sql/modeling_%s.sql", db);
        try {
            new SqlScriptRunner(dataSource, sqlFilePath).execute();
            ensureDimensionFieldSchema();
            ensureDorisPublishSchema();
            log.info("Ensured data modeling schema using {}", sqlFilePath);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize data modeling schema using " + sqlFilePath, ex);
        }
    }

    /**
     * Add the DIM business identifier to existing dimension-model tables and
     * preserve historical DIM identifiers by copying the former table_name value.
     */
    private void ensureDimensionShortNameColumn() {
        final String tableName = "t_yak_ops_dimension_table";
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!tableExists(metadata, connection, tableName)) {
                return;
            }
            if (!columnExists(metadata, connection, tableName, "en_short_name")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE " + quoteIdentifier(tableName)
                            + " ADD COLUMN en_short_name varchar(255) NULL");
                }
                log.info("Added en_short_name to {}", tableName);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + quoteIdentifier(tableName)
                            + " SET en_short_name = table_name"
                            + " WHERE table_type = ?"
                            + " AND (en_short_name IS NULL OR TRIM(en_short_name) = '')"
                            + " AND table_name IS NOT NULL AND TRIM(table_name) <> ''")) {
                statement.setString(1, "DIM");
                int migrated = statement.executeUpdate();
                if (migrated > 0) {
                    log.info("Migrated {} historical DIM short names", migrated);
                }
            }
            if (!indexExists(metadata, connection, tableName, "idx_yak_dimension_table_6")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE INDEX idx_yak_dimension_table_6 ON "
                            + quoteIdentifier(tableName) + " (project_code, en_short_name)");
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to ensure dimension short-name schema", ex);
        }
    }

    /**
     * Additive upgrade for Doris physical-model field metadata. Legacy rows receive stable
     * ordinals in create_time order, while already assigned ordinals remain unchanged.
     */
    private void ensureDimensionFieldSchema() {
        final String tableName = "t_yak_ops_dimension_table_fields";
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!tableExists(metadata, connection, tableName)) {
                return;
            }
            ensureColumn(metadata, connection, tableName, "ordinal", "integer NULL");
            ensureColumn(metadata, connection, tableName, "field_scale", "integer NULL");
            ensureColumn(metadata, connection, tableName, "datetime_precision", "integer NULL");
            ensureColumn(metadata, connection, tableName, "default_value", "varchar(255) NULL");
            ensureColumn(metadata, connection, tableName, "primary_flag", "boolean NULL");
            ensureColumn(metadata, connection, tableName, "can_null", "boolean NULL");
            migrateMissingDimensionFieldOrdinals(connection, tableName);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to ensure dimension field schema", ex);
        }
    }

    private void ensureColumn(
            DatabaseMetaData metadata,
            Connection connection,
            String tableName,
            String columnName,
            String definition) throws SQLException {
        if (!columnExists(metadata, connection, tableName, columnName)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + quoteIdentifier(tableName)
                        + " ADD COLUMN " + columnName + " " + definition);
            }
            log.info("Added {} to {}", columnName, tableName);
        }
    }

    /**
     * Persist the resolved physical configuration with each publish record and retire the
     * former mutable Doris-config tables. Existing configured Key fields are migrated to the
     * logical field primaryFlag before their duplicate configuration tables are dropped.
     */
    private void ensureDorisPublishSchema() {
        final String publishTable = "t_yak_ops_dimension_table_publish";
        final String fieldTable = "t_yak_ops_dimension_table_fields";
        final String[] legacyKeyTables = {
                "t_yak_ops_dimension_table_doris_key_fields",
                "t_cockpit_dimension_table_doris_key_fields"
        };
        final String[] retiredTables = {
                "t_yak_ops_dimension_table_doris_distribution_fields",
                "t_yak_ops_dimension_table_doris_key_fields",
                "t_yak_ops_dimension_table_doris_config",
                "t_cockpit_dimension_table_doris_distribution_fields",
                "t_cockpit_dimension_table_doris_key_fields",
                "t_cockpit_dimension_table_doris_config"
        };
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!tableExists(metadata, connection, publishTable)) {
                return;
            }
            String physicalConfigType = "mysql".equalsIgnoreCase(dbType.getDb())
                    ? "longtext NULL" : "text NULL";
            ensureColumn(metadata, connection, publishTable, "physical_config", physicalConfigType);
            ensureColumn(metadata, connection, publishTable, "physical_schema", physicalConfigType);
            ensureColumn(metadata, connection, publishTable, "table_model", "varchar(32) NULL");
            ensureColumn(metadata, connection, publishTable, "publish_mode", "varchar(32) NULL");
            ensureColumn(metadata, connection, publishTable, "publish_remark", "text NULL");
            ensureColumn(metadata, connection, publishTable, "key_fields", "text NULL");

            if (tableExists(metadata, connection, fieldTable)) {
                try (Statement statement = connection.createStatement()) {
                    int normalized = statement.executeUpdate(
                            "UPDATE " + quoteIdentifier(fieldTable)
                                    + " SET can_null = FALSE"
                                    + " WHERE primary_flag = TRUE AND (can_null IS NULL OR can_null = TRUE)");
                    if (normalized > 0) {
                        log.info("Normalized {} primary fields to NOT NULL", normalized);
                    }
                }
                for (String keyTable : legacyKeyTables) {
                    if (!tableExists(metadata, connection, keyTable)) {
                        continue;
                    }
                    String migrateSql = "UPDATE " + quoteIdentifier(fieldTable)
                            + " SET primary_flag = TRUE, can_null = FALSE"
                            + " WHERE id IN (SELECT field_id FROM " + quoteIdentifier(keyTable)
                            + " WHERE table_id = " + quoteIdentifier(fieldTable) + ".table_id)";
                    try (Statement statement = connection.createStatement()) {
                        int migrated = statement.executeUpdate(migrateSql);
                        if (migrated > 0) {
                            log.info("Migrated {} legacy Doris Key fields from {}", migrated, keyTable);
                        }
                    }
                }
            }

            for (String retiredTable : retiredTables) {
                if (tableExists(metadata, connection, retiredTable)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("DROP TABLE " + quoteIdentifier(retiredTable));
                    }
                    log.info("Retired legacy Doris configuration table {}", retiredTable);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to migrate Doris publish configuration schema", ex);
        }
    }

    /**
     * Backfill field ordinals for installations created before the ordinal column existed.
     * Fully legacy tables are ordered by create_time and id; partially migrated tables retain
     * their explicit ordinals and receive missing values after the current maximum.
     */
    private void migrateMissingDimensionFieldOrdinals(Connection connection, String tableName)
            throws SQLException {
        java.util.Map<String, java.util.List<FieldOrdinalRow>> rowsByTable =
                new java.util.LinkedHashMap<>();
        String selectSql = "SELECT id, table_id, ordinal FROM " + quoteIdentifier(tableName)
                + " ORDER BY table_id, CASE WHEN create_time IS NULL THEN 1 ELSE 0 END, create_time, id";
        try (PreparedStatement statement = connection.prepareStatement(selectSql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String tableId = result.getString("table_id");
                int value = result.getInt("ordinal");
                Integer ordinal = result.wasNull() ? null : value;
                rowsByTable.computeIfAbsent(tableId, key -> new java.util.ArrayList<>())
                        .add(new FieldOrdinalRow(result.getString("id"), ordinal));
            }
        }

        String updateSql = "UPDATE " + quoteIdentifier(tableName)
                + " SET ordinal = ? WHERE id = ? AND ordinal IS NULL";
        int migratedRows = 0;
        boolean originalAutoCommit = connection.getAutoCommit();
        if (originalAutoCommit) {
            connection.setAutoCommit(false);
        }
        try (PreparedStatement update = connection.prepareStatement(updateSql)) {
            for (java.util.List<FieldOrdinalRow> rows : rowsByTable.values()) {
                boolean allOrdinalsMissing = rows.stream().allMatch(row -> row.ordinal == null);
                int nextOrdinal = rows.stream()
                        .filter(row -> row.ordinal != null)
                        .mapToInt(row -> row.ordinal)
                        .max()
                        .orElse(-1) + 1;
                for (int index = 0; index < rows.size(); index++) {
                    FieldOrdinalRow row = rows.get(index);
                    if (row.ordinal != null) {
                        continue;
                    }
                    update.setInt(1, allOrdinalsMissing ? index : nextOrdinal++);
                    update.setString(2, row.id);
                    update.addBatch();
                    migratedRows++;
                }
            }
            if (migratedRows > 0) {
                update.executeBatch();
            }
            if (originalAutoCommit) {
                connection.commit();
            }
        } catch (SQLException ex) {
            if (originalAutoCommit) {
                connection.rollback();
            }
            throw ex;
        } finally {
            if (originalAutoCommit) {
                connection.setAutoCommit(true);
            }
        }
        if (migratedRows > 0) {
            log.info("Migrated {} historical dimension field ordinals in {}", migratedRows, tableName);
        }
    }

    private static final class FieldOrdinalRow {
        private final String id;
        private final Integer ordinal;

        private FieldOrdinalRow(String id, Integer ordinal) {
            this.id = id;
            this.ordinal = ordinal;
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
        if (indexName.startsWith("idx_yak_") || indexName.startsWith("uk_yak_")) {
            return indexName;
        }
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

    private boolean indexExists(DatabaseMetaData metadata, Connection connection, String tableName, String indexName)
            throws SQLException {
        try (ResultSet indexes = metadata.getIndexInfo(
                connection.getCatalog(), connection.getSchema(), tableName, false, false)) {
            while (indexes.next()) {
                String actualName = indexes.getString("INDEX_NAME");
                if (actualName != null && indexName.equalsIgnoreCase(actualName)) {
                    return true;
                }
            }
        }
        try (ResultSet indexes = metadata.getIndexInfo(
                connection.getCatalog(), connection.getSchema(), tableName.toUpperCase(Locale.ROOT), false, false)) {
            while (indexes.next()) {
                String actualName = indexes.getString("INDEX_NAME");
                if (actualName != null && indexName.equalsIgnoreCase(actualName)) {
                    return true;
                }
            }
        }
        return false;
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
