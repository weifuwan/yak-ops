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

package io.yak.ops.connector.cdc.mysql.source.config;

import io.debezium.relational.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Properties;
import java.time.Duration;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests for {@link MySqlSourceConfig}. */
class MySqlSourceConfigTest {

    private static MySqlSourceConfigFactory settings() {
        return new MySqlSourceConfigFactory()
                .hostname("localhost")
                .username("reader")
                .password("secret")
                .topicPrefix("mysql-config-contract")
                .serverId(9000)
                .table(
                        new io.yak.ops.core.data.TableId("shop", null, "orders"),
                        new TableSchema(
                                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                                List.of("id")));
    }

    @Test
    void effectiveJdbcHeartbeatBufferAndFetchSettingsAreAppliedToRuntime() {
        var jdbc = new Properties();
        jdbc.setProperty("useSSL", "false");
        MySqlSourceConfig config = settings()
                .jdbcProperties(jdbc)
                .fetchSize(73)
                .splitMetaGroupSize(17)
                .distributionFactorLower(0.2)
                .distributionFactorUpper(5.0)
                .connectTimeout(Duration.ofMillis(3500))
                .heartbeatInterval(Duration.ofMillis(750))
                .queueCapacity(16)
                .createConfig(0);

        assertThat(config.getFetchSize()).isEqualTo(73);
        assertThat(config.getSplitMetaGroupSize()).isEqualTo(17);
        assertThat(config.getDistributionFactorLower()).isEqualTo(0.2);
        assertThat(config.getDistributionFactorUpper()).isEqualTo(5.0);
        assertThat(config.getConnectTimeout()).isEqualTo(Duration.ofMillis(3500));
        assertThat(config.getHeartbeatInterval()).isEqualTo(Duration.ofMillis(750));
        assertThat(config.connectionProperties()).containsEntry("useSSL", "false");
        assertThat(config.getJdbcProperties().getProperty("useSSL")).isEqualTo("false");

        var props = config.debeziumProperties("test", Path.of("unused-history.dat"));
        assertThat(props.getProperty("heartbeat.interval.ms")).isEqualTo("750");
        assertThat(props.getProperty("max.queue.size")).isEqualTo("16");
        assertThat(props.getProperty("max.batch.size")).isEqualTo("15");
        assertThat(props.getProperty("driver.useSSL")).isEqualTo("false");
        assertThat(props.getProperty("driver.connectTimeout")).isEqualTo("3500");
        assertThat(props.getProperty("snapshot.mode")).isEqualTo("no_data");
        assertThat(props.getProperty("database.password")).isEqualTo("secret");
    }

    @Test
    void defaultHeartbeatMatchesActualDebeziumBootstrapContract() {
        var config = settings().createConfig(0);
        var properties = config.debeziumProperties("test", Path.of("unused-history.dat"));
        assertThat(MySqlSourceOptions.HEARTBEAT_INTERVAL.defaultValue()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getProperty("heartbeat.interval.ms")).isEqualTo("1000");
        assertThat(config.getConnectTimeout()).isEqualTo(MySqlSourceOptions.CONNECT_TIMEOUT.defaultValue());
        assertThat(properties.getProperty("max.queue.size")).isEqualTo("256");
        assertThat(properties.getProperty("max.batch.size")).isEqualTo("128");
    }

    @Test
    void minimumQueueCapacityStillGivesDebeziumMoreQueueSlotsThanBatchSlots() {
        var props = settings().queueCapacity(1).createConfig(0)
                .debeziumProperties("test", Path.of("unused-history.dat"));
        assertThat(props.getProperty("max.queue.size")).isEqualTo("2");
        assertThat(props.getProperty("max.batch.size")).isEqualTo("1");
    }

    @Test
    void rejectsInertOrInconsistentSettingsBeforeOpeningMySql() {
        assertThatThrownBy(() -> settings().connectionProperties(java.util.Map.of("connectTimeout", "500"))
                        .createConfig(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("connect.timeout");
        assertThatThrownBy(() -> settings().fetchSize(0).createConfig(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scan.snapshot.fetch.size");
        assertThatThrownBy(() -> settings().splitMetaGroupSize(0).createConfig(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chunk-meta.group.size");
        assertThatThrownBy(() -> settings().connectTimeout(Duration.ofMillis(249)).createConfig(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("connect.timeout");
        assertThatThrownBy(() -> settings().heartbeatInterval(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("heartbeat.interval");
        assertThatThrownBy(() -> settings().distributionFactorLower(Double.NaN).createConfig(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("even-distribution");
        assertThatThrownBy(() -> settings().releaseSnapshotMetadataEnabled(true)
                        .scanNewlyAddedTableEnabled(true).createConfig(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("conflicts");
        assertThatThrownBy(() -> settings().startupOptions(StartupOptions.latest())
                        .skipSnapshotBackfill(true).createConfig(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Binlog-only");
        assertThatThrownBy(() -> settings().startupOptions(StartupOptions.snapshot())
                        .scanNewlyAddedTableEnabled(true).createConfig(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Bounded");
    }

    @Test
    void testTableFilterWithExcludeTableList() {
        MySqlSourceConfig config =
                new MySqlSourceConfigFactory()
                        .hostname("localhost")
                        .username("user")
                        .password("password")
                        .databaseList("test_db")
                        .tableList("test_db\\.orders_.*")
                        .excludeTableList("test_db.orders_skip")
                        .topicPrefix("yak_mysql_test")
                        .serverId(6001)
                        .table(
                                new io.yak.ops.core.data.TableId("test_db", null, "orders_1"),
                                new TableSchema(
                                        List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                                        List.of("id")))
                        .createConfig(0);

        Predicate<TableId> tableFilter = config.getTableFilter();
        TableId includedTable = new TableId("test_db", null, "orders_1");
        TableId excludedTable = new TableId("test_db", null, "orders_skip");
        TableId unmatchedTable = new TableId("test_db", null, "customers");

        assertThat(tableFilter.test(includedTable)).isTrue();
        assertThat(tableFilter.test(excludedTable)).isFalse();
        assertThat(tableFilter.test(unmatchedTable)).isFalse();

        // Debezium 2.7 forbids table.include.list and table.exclude.list together.
        // Verify the actual connector configuration, not only the Java predicate.
        String include = config.getDbzProperties().getProperty("table.include.list");
        assertThat(config.getDbzProperties().getProperty("table.exclude.list")).isNull();
        assertThat(Pattern.compile(include).matcher("test_db.orders_1").matches()).isTrue();
        assertThat(Pattern.compile(include).matcher("test_db.orders_skip").matches()).isFalse();

        var runtime = config.debeziumProperties("unit-test", Path.of("unused-history.dat"));
        assertThat(runtime.getProperty("table.exclude.list")).isNull();
        String runtimeInclude = runtime.getProperty("table.include.list");
        assertThat(Pattern.compile(runtimeInclude).matcher("test_db.orders_1").matches()).isTrue();
        assertThat(Pattern.compile(runtimeInclude).matcher("test_db.orders_skip").matches()).isFalse();
    }
}
