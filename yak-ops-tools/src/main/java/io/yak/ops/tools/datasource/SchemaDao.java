package io.yak.ops.tools.datasource;

import io.yak.ops.common.sql.SqlScriptRunner;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
     * Execute the complete baseline schema on an empty database only.
     * Call through YakOpsManager to enforce the safety checks.
     */
    /**
     * The baseline SQL contains DROP TABLE statements. Only run it in an empty schema.
     */
    public boolean hasExistingTables() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(
                    connection.getCatalog(), connection.getSchema(), "%", new String[] {"TABLE"})) {
                return tables.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to inspect existing database tables", ex);
        }
    }

    public void initSchema() {
        String sqlFilePath = String.format("sql/yak-ops_%s.sql", dbType.getDb());
        try {
            new SqlScriptRunner(dataSource, sqlFilePath).execute();
            log.info("Initialized database using {}", sqlFilePath);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize database using " + sqlFilePath, ex);
        }
    }

    public String getCurrentVersion() {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT version FROM t_ds_version");
                ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getString(1) : null;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read t_ds_version", ex);
        }
    }
}
