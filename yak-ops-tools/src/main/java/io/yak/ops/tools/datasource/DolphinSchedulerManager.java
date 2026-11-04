/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.tools.datasource;

import io.yak.ops.dao.plugin.api.dialect.DatabaseDialect;
import io.yak.ops.tools.datasource.utils.SchemaUtils;

import java.io.IOException;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DolphinSchedulerManager {

    private final SchemaDao schemaDao;

    private final DatabaseDialect databaseDialect;

    public DolphinSchedulerManager(SchemaDao schemaDao, DatabaseDialect databaseDialect) {
        this.schemaDao = schemaDao;
        this.databaseDialect = databaseDialect;
    }

    /**
     * Initialize a new database from the 3.4.3 schema or validate an existing database.
     * Historical database upgrades are intentionally unsupported.
     */
    public void initializeOrValidate() throws IOException {
        if (databaseDialect.tableExists("t_ds_version")) {
            validateCurrentVersion();
            log.info("Database schema is already initialized at version {}", SchemaUtils.getSoftVersion());
            return;
        }

        // The full initialization SQL contains DROP TABLE statements. Never execute it over
        // a partially initialized database or an unsupported historical installation.
        if (databaseDialect.tableExists("t_escheduler_version")
                || databaseDialect.tableExists("t_escheduler_queue")
                || databaseDialect.tableExists("t_ds_user")
                || databaseDialect.tableExists("t_ds_workflow_instance")
                || databaseDialect.tableExists("QRTZ_JOB_DETAILS")) {
            throw new IllegalStateException(
                    "Existing DolphinScheduler tables detected without t_ds_version; "
                            + "refusing destructive schema initialization");
        }

        log.info("Initializing database schema from the 3.4.3 baseline");
        schemaDao.initSchema();
        validateCurrentVersion();
        log.info("Database schema initialization finished");
    }

    public void validateCurrentVersion() throws IOException {
        if (!databaseDialect.tableExists("t_ds_version")) {
            throw new IllegalStateException("Required t_ds_version table is missing");
        }

        String expectedVersion = SchemaUtils.getSoftVersion();
        String actualVersion = schemaDao.getCurrentVersion();
        if (!expectedVersion.equals(actualVersion)) {
            throw new IllegalStateException(
                    "Unsupported database schema version " + actualVersion
                            + "; Yak Ops requires " + expectedVersion
                            + " and does not support historical database upgrades");
        }

        if (!databaseDialect.tableExists("t_ds_user")
                || !databaseDialect.tableExists("t_ds_workflow_instance")
                || !databaseDialect.tableExists("t_ds_task_instance")) {
            throw new IllegalStateException(
                    "Database schema version matches but required 3.4.3 tables are missing");
        }
    }
}
