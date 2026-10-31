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
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link MySqlSourceConfig}. */
class MySqlSourceConfigTest {

    @Test
    void independentAnchoredDiscoveryPatternsOnlyCaptureAdmittedTables() {
        var first = new io.yak.ops.core.data.TableId("test_db", null, "orders_1");
        var second = new io.yak.ops.core.data.TableId("test_db", null, "orders_2");
        var schema = new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        MySqlSourceConfig config = new MySqlSourceConfigFactory()
                .hostname("localhost").username("user").password("password")
                .databaseList("^(?:test_db)$").discoveryTablePattern("^(?:orders_[0-9]+)$")
                .scanNewlyAddedTableEnabled(true).topicPrefix("anchored-discovery").serverId(6101)
                .table(first, schema).createConfig(0);
        assertThat(config.getTableFilter().test(new TableId("test_db", null, "orders_2"))).isTrue();
        assertThat(config.getTableFilter().test(new TableId("other", null, "orders_2"))).isFalse();
        assertThat(config.getTableFilter().test(new TableId("test_db", null, "customers"))).isFalse();
        String initial = config.debeziumProperties("initial", Path.of("unused-history.dat"))
                .getProperty("table.include.list");
        assertThat(Pattern.matches(initial, "test_db.orders_1")).isTrue();
        assertThat(Pattern.matches(initial, "test_db.orders_2")).isFalse();
        config.registerDiscoveredTable(second, config.physicalSchemas().get(first));
        String expanded = config.debeziumProperties("expanded", Path.of("unused-history.dat"))
                .getProperty("table.include.list");
        assertThat(java.util.Arrays.stream(expanded.split(",")).anyMatch(pattern ->
                Pattern.matches(pattern, "test_db.orders_2"))).isTrue();
        assertThat(config.discoveryCopy().getTableFilter().test(new TableId("test_db", null, "orders_3")))
                .isTrue();
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
