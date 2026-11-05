package io.yak.ops.tools.datasource;

import io.yak.ops.dao.plugin.api.dialect.DatabaseDialect;
import io.yak.ops.tools.datasource.utils.SchemaUtils;

import java.io.IOException;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class YakOpsManager {

    private final SchemaDao schemaDao;

    private final DatabaseDialect databaseDialect;

    public YakOpsManager(SchemaDao schemaDao, DatabaseDialect databaseDialect) {
        this.schemaDao = schemaDao;
        this.databaseDialect = databaseDialect;
    }

    /**
     * Initialize a new database from the 3.4.3 schema or validate an existing database.
     * Historical database upgrades are intentionally unsupported.
     */
    public void initializeOrValidate() throws IOException {
        if (schemaDao.hasLegacySchemaTables()) {
            throw new IllegalStateException(
                    "Legacy DolphinScheduler database tables detected; "
                            + "migrate them explicitly before starting Yak Ops");
        }

        if (databaseDialect.tableExists("t_yak_ops_version")) {
            validateCurrentVersion();
            log.info("Database schema is already initialized at version {}", SchemaUtils.getSoftVersion());
            return;
        }

        // The full initialization SQL contains DROP TABLE statements. Never execute it over
        // a partially initialized database or an unsupported historical installation.
        if (schemaDao.hasExistingTables()) {
            throw new IllegalStateException(
                    "Existing database tables detected without t_yak_ops_version; "
                            + "refusing destructive schema initialization");
        }

        log.info("Initializing database schema from the 3.4.3 baseline");
        schemaDao.initSchema();
        validateCurrentVersion();
        log.info("Database schema initialization finished");
    }

    public void validateCurrentVersion() throws IOException {
        if (!databaseDialect.tableExists("t_yak_ops_version")) {
            throw new IllegalStateException("Required t_yak_ops_version table is missing");
        }

        String expectedVersion = SchemaUtils.getSoftVersion();
        String actualVersion = schemaDao.getCurrentVersion();
        if (!expectedVersion.equals(actualVersion)) {
            throw new IllegalStateException(
                    "Unsupported database schema version " + actualVersion
                            + "; Yak Ops requires " + expectedVersion
                            + " and does not support historical database upgrades");
        }

        if (!databaseDialect.tableExists("t_yak_ops_user")
                || !databaseDialect.tableExists("t_yak_ops_workflow_instance")
                || !databaseDialect.tableExists("t_yak_ops_task_instance")) {
            throw new IllegalStateException(
                    "Database schema version matches but required Yak Ops tables are missing");
        }
    }
}
