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

package io.yak.ops.tools.datasource.mysql.v5;

import io.yak.ops.tools.datasource.DolphinSchedulerManager;
import io.yak.ops.tools.datasource.jupiter.DolphinSchedulerDatabaseContainer;
import io.yak.ops.tools.datasource.mysql.DolphinSchedulerMysqlProfile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DolphinSchedulerMysqlProfile
@DolphinSchedulerDatabaseContainer(imageName = "mysql:5.7")
class InitializeSchemaIT {

    @Autowired
    private DolphinSchedulerManager manager;

    @Autowired
    private DataSource dataSource;

    @Test
    void initializeBaselineAndPreserveExistingData() throws Exception {
        manager.initializeOrValidate();

        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("SELECT version FROM t_ds_version")) {
                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals("3.4.3", resultSet.getString(1));
            }
            Assertions.assertEquals(1, statement.executeUpdate(
                    "UPDATE t_ds_user SET user_name = 'baseline_preserved' WHERE id = 1"));
        }

        // An initialized database must not be replaced by the DROP TABLE-based full SQL.
        manager.initializeOrValidate();
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT user_name FROM t_ds_user WHERE id = 1")) {
            Assertions.assertTrue(resultSet.next());
            Assertions.assertEquals("baseline_preserved", resultSet.getString(1));
        }
    }
}
