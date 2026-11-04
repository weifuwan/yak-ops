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

import io.yak.ops.common.sql.SqlScriptRunner;

import java.sql.Connection;
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
     * Call through DolphinSchedulerManager to enforce the safety checks.
     */
    public void initSchema() {
        String sqlFilePath = String.format("sql/dolphinscheduler_%s.sql", dbType.getDb());
        try {
            new SqlScriptRunner(dataSource, sqlFilePath).execute();
            log.info("Initialized database using {}", sqlFilePath);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize database using " + sqlFilePath, ex);
        }
    }

    public String getCurrentVersion() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT version FROM t_ds_version");
                ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getString(1) : null;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read t_ds_version", ex);
        }
    }
}
