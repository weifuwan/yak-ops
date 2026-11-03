/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.cdc.connectors.jdbc.mysql;

import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.cdc.common.pipeline.PipelineOptions;
import org.apache.flink.cdc.common.pipeline.SchemaChangeBehavior;
import org.apache.flink.cdc.composer.PipelineExecution;
import org.apache.flink.cdc.composer.definition.PipelineDef;
import org.apache.flink.cdc.composer.definition.RouteDef;
import org.apache.flink.cdc.composer.definition.SinkDef;
import org.apache.flink.cdc.composer.definition.SourceDef;
import org.apache.flink.cdc.composer.flink.FlinkPipelineComposer;
import org.apache.flink.cdc.connectors.jdbc.factory.JdbcDataSinkFactory;
import org.apache.flink.cdc.connectors.jdbc.options.JdbcSinkOptions;
import org.apache.flink.cdc.connectors.mysql.factory.MySqlDataSourceFactory;
import org.apache.flink.cdc.connectors.mysql.source.MySqlDataSourceOptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the production MySQL CDC source, composer, routing, metadata applier and JDBC sink
 * against a real MySQL container. Snapshot mode keeps the test bounded and deterministic.
 */
class MySqlPipelineToJdbcITCase extends MySqlSinkTestBase {

    private static final String SOURCE_DATABASE = "yak_jdbc_source";
    private static final String TARGET_DATABASE = "yak_jdbc_target";

    @BeforeEach
    void prepareDatabases() throws Exception {
        try (Connection connection = MYSQL_CONTAINER.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + SOURCE_DATABASE);
            statement.execute("CREATE DATABASE IF NOT EXISTS " + TARGET_DATABASE);
            statement.execute("CREATE TABLE " + SOURCE_DATABASE
                    + ".orders (id BIGINT NOT NULL PRIMARY KEY, order_no VARCHAR(64) NOT NULL)");
            statement.execute("INSERT INTO " + SOURCE_DATABASE
                    + ".orders (id, order_no) VALUES (1, 'A001'), (2, 'A002')");
        }
    }

    @AfterEach
    void cleanDatabases() throws Exception {
        try (Connection connection = MYSQL_CONTAINER.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + SOURCE_DATABASE);
            statement.execute("DROP DATABASE IF EXISTS " + TARGET_DATABASE);
        }
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void testSnapshotAutoCreatesRoutedTableAndWritesRows() throws Exception {
        Configuration sourceConfig = new Configuration();
        sourceConfig.set(MySqlDataSourceOptions.HOSTNAME, MYSQL_CONTAINER.getHost());
        sourceConfig.set(MySqlDataSourceOptions.PORT, MYSQL_CONTAINER.getDatabasePort());
        sourceConfig.set(MySqlDataSourceOptions.USERNAME, MYSQL_CONTAINER.getUsername());
        sourceConfig.set(MySqlDataSourceOptions.PASSWORD, MYSQL_CONTAINER.getPassword());
        sourceConfig.set(MySqlDataSourceOptions.TABLES, SOURCE_DATABASE + "\\.orders");
        sourceConfig.set(MySqlDataSourceOptions.SERVER_ID, "5400-5404");
        sourceConfig.set(MySqlDataSourceOptions.SERVER_TIME_ZONE, "UTC");
        sourceConfig.set(MySqlDataSourceOptions.SCAN_STARTUP_MODE, "snapshot");

        Configuration sinkConfig = new Configuration();
        sinkConfig.set(
                JdbcSinkOptions.CONN_URL,
                "jdbc:mysql://" + MYSQL_CONTAINER.getHost() + ":" + MYSQL_CONTAINER.getDatabasePort());
        sinkConfig.set(JdbcSinkOptions.USERNAME, MYSQL_CONTAINER.getUsername());
        sinkConfig.set(JdbcSinkOptions.PASSWORD, MYSQL_CONTAINER.getPassword());
        sinkConfig.set(JdbcSinkOptions.SERVER_TIME_ZONE, "UTC");

        Configuration pipelineConfig = new Configuration();
        pipelineConfig.set(PipelineOptions.PIPELINE_PARALLELISM, 1);
        pipelineConfig.set(
                PipelineOptions.PIPELINE_SCHEMA_CHANGE_BEHAVIOR, SchemaChangeBehavior.EVOLVE);

        PipelineDef definition = new PipelineDef(
                new SourceDef(MySqlDataSourceFactory.IDENTIFIER, "MySQL CDC", sourceConfig),
                new SinkDef(JdbcDataSinkFactory.IDENTIFIER, "MySQL JDBC", sinkConfig),
                Collections.emptyList(),
                Collections.singletonList(
                        new RouteDef(SOURCE_DATABASE + "\\.orders", TARGET_DATABASE + ".orders",
                                null, null)),
                Collections.emptyList(),
                pipelineConfig);

        PipelineExecution execution = FlinkPipelineComposer.ofMiniCluster().compose(definition);
        execution.execute();

        try (Connection connection = MYSQL_CONTAINER.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT id, order_no FROM " + TARGET_DATABASE + ".orders ORDER BY id")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong("id")).isEqualTo(1L);
            assertThat(result.getString("order_no")).isEqualTo("A001");
            assertThat(result.next()).isTrue();
            assertThat(result.getLong("id")).isEqualTo(2L);
            assertThat(result.getString("order_no")).isEqualTo("A002");
            assertThat(result.next()).isFalse();
        }
    }
}
