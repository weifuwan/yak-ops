package io.yak.ops.plugin.database.doris;

import io.yak.ops.plugin.database.jdbc.GenericJdbcCatalog;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Doris 专用 Catalog，实现 SHOW DATABASES / SHOW FULL TABLES 元数据发现。 */
public final class DorisJdbcCatalog extends GenericJdbcCatalog {

    private final JdbcConnectionProperties connection;
    private final int timeoutSeconds;
    private final JdbcConnectionProvider connectionProvider;

    public DorisJdbcCatalog(
            JdbcConnectionProperties connection, int timeoutSeconds, JdbcConnectionProvider connectionProvider) {
        super(connection, timeoutSeconds);
        this.connection = connection;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.connectionProvider = connectionProvider;
    }

    @Override
    protected Connection openConnection() throws Exception {
        return connectionProvider.open(connection, timeoutSeconds);
    }

    @Override
    public List<String> listDatabases() {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SHOW DATABASES")) {
            List<String> databases = new ArrayList<>();
            while (resultSet.next()) {
                String database = resultSet.getString(1);
                if (includeDatabase(database)) {
                    databases.add(database);
                }
            }
            return databases;
        } catch (Exception exception) {
            throw catalogError("读取 Doris 数据库列表失败", exception);
        }
    }

    @Override
    public List<String> listSchemas(String database) {
        return Collections.emptyList();
    }

    @Override
    public List<DataSourceTable> listTables(DataSourceCatalogQuery query) {
        String database = query == null ? null : query.getDatabase();
        if (database == null || database.trim().isEmpty()) {
            database = connectionProperties().database();
        }
        if (database == null || database.trim().isEmpty()) {
            throw catalogError("Doris database 不能为空", null);
        }

        String keyword = query == null ? null : query.getKeyword();
        String sql = "SHOW FULL TABLES FROM " + quoteIdentifier(database);
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            List<DataSourceTable> tables = new ArrayList<>();
            while (resultSet.next()) {
                String table = resultSet.getString(1);
                if (!matchesKeyword(table, keyword)) {
                    continue;
                }
                tables.add(new DataSourceTable(database, null, table, resultSet.getString(2), null));
            }
            return tables;
        } catch (Exception exception) {
            throw catalogError("读取 Doris 表列表失败", exception);
        }
    }

    @Override
    protected boolean includeDatabase(String database) {
        if (!super.includeDatabase(database)) {
            return false;
        }
        return !"information_schema".equalsIgnoreCase(database)
                && !"mysql".equalsIgnoreCase(database)
                && !"__internal_schema".equalsIgnoreCase(database);
    }

    @Override
    protected String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
