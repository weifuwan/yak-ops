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

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import java.time.Duration;

/** Configurations for {@link MySqlSource}. */
public class MySqlSourceOptions {
    public static final Option<String> DISCOVERY_DEFAULTS =
            Options.key("scan.newly-added-table.defaults").stringType().noDefaultValue();

    public static final Option<Boolean> SNAPSHOT_SKIP =
            Options.key("snapshot.skip").booleanType().defaultValue(false);
    public static final Option<String> SNAPSHOT_WHERE =
            Options.key("snapshot.where").stringType().noDefaultValue();
    public static final Option<Boolean> DML_INSERT =
            Options.key("dml.insert").booleanType().defaultValue(true);
    public static final Option<Boolean> DML_UPDATE =
            Options.key("dml.update").booleanType().defaultValue(true);
    public static final Option<Boolean> DML_DELETE =
            Options.key("dml.delete").booleanType().defaultValue(true);

    public static final Option<String> HOSTNAME = Options.key("hostname")
            .stringType()
            .noDefaultValue()
            .withDescription("IP address or hostname of the MySQL database server.");

    public static final Option<Integer> PORT = Options.key("port")
            .intType()
            .defaultValue(3306)
            .withDescription("Integer port number of the MySQL database server.");

    public static final Option<String> USERNAME = Options.key("username")
            .stringType()
            .noDefaultValue()
            .withDescription("Name of the MySQL database to use when connecting to the MySQL database server.");

    public static final Option<String> PASSWORD = Options.key("password")
            .stringType()
            .noDefaultValue()
            .withDescription("Password to use when connecting to the MySQL database server.");

    public static final Option<String> DATABASE_NAME = Options.key("database-name")
            .stringType()
            .noDefaultValue()
            .withDescription("Database name of the MySQL server to monitor.");

    public static final Option<String> TABLE_NAME = Options.key("table-name")
            .stringType()
            .noDefaultValue()
            .withDescription("Table name of the MySQL database to monitor.");

    public static final Option<String> SERVER_TIME_ZONE = Options.key("server-time-zone")
            .stringType()
            .noDefaultValue()
            .withDescription("The session time zone in database server. If not set, then "
                    + "ZoneId.systemDefault() is used to determine the server time zone.");

    public static final Option<String> SERVER_ID = Options.key("server-id")
            .stringType()
            .noDefaultValue()
            .withDescription("A numeric ID or a numeric ID range of this database client, "
                    + "The numeric ID syntax is like '5400', the numeric ID range syntax "
                    + "is like '5400-5408', The numeric ID range syntax is recommended when "
                    + "'scan.incremental.snapshot.enabled' enabled. Every ID must be unique across all "
                    + "currently-running database processes in the MySQL cluster. This connector"
                    + " joins the MySQL  cluster as another server (with this unique ID) "
                    + "so it can read the binlog. By default, a random number is generated between"
                    + " 5400 and 6400, though we recommend setting an explicit value.");

    public static final Option<Boolean> SCAN_INCREMENTAL_SNAPSHOT_ENABLED = Options.key(
                    "scan.incremental.snapshot.enabled")
            .booleanType()
            .defaultValue(true)
            .withDescription(
                    "Incremental snapshot is a new mechanism to read snapshot of a table. "
                            + "Compared to the old snapshot mechanism, the incremental snapshot has many advantages, including:\n"
                            + "(1) source can be parallel during snapshot reading, \n"
                            + "(2) source can perform checkpoints in the chunk granularity during snapshot reading, \n"
                            + "(3) source doesn't need to acquire global read lock (FLUSH TABLES WITH READ LOCK) before snapshot reading.\n"
                            + "If you would like the source run in parallel, each parallel reader should have an unique server id, "
                            + "so the 'server-id' must be a range like '5400-6400', and the range must be larger than the parallelism.");

    public static final Option<Integer> SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE = Options.key(
                    "scan.incremental.snapshot.chunk.size")
            .intType()
            .defaultValue(8096)
            .withDescription(
                    "The chunk size (number of rows) of table snapshot, captured tables are split into multiple chunks when read the snapshot of table.");

    public static final Option<Integer> SCAN_SNAPSHOT_FETCH_SIZE = Options.key("scan.snapshot.fetch.size")
            .intType()
            .defaultValue(1024)
            .withDescription("The maximum fetch size for per poll when read table snapshot.");

    public static final Option<Duration> CONNECT_TIMEOUT = Options.key("connect.timeout")
            .durationType()
            .defaultValue(Duration.ofSeconds(30))
            .withDescription(
                    "The maximum time that the connector should wait after trying to connect to the MySQL database server before timing out. This value cannot be less than 250ms.");

    public static final Option<Integer> CONNECTION_POOL_SIZE =
            Options.key("connection.pool.size").intType().defaultValue(20).withDescription("The connection pool size.");

    public static final Option<Integer> CONNECT_MAX_RETRIES = Options.key("connect.max-retries")
            .intType()
            .defaultValue(3)
            .withDescription(
                    "The max retry times that the connector should retry to build MySQL database server connection.");

    public static final Option<String> SCAN_STARTUP_MODE = Options.key("scan.startup.mode")
            .stringType()
            .defaultValue("initial")
            .withDescription("Optional startup mode for MySQL CDC consumer, valid enumerations are "
                    + "\"initial\", \"earliest-offset\", \"latest-offset\", \"timestamp\"\n"
                    + "or \"specific-offset\"");

    public static final Option<String> SCAN_STARTUP_SPECIFIC_OFFSET_FILE = Options.key(
                    "scan.startup.specific-offset.file")
            .stringType()
            .noDefaultValue()
            .withDescription("Optional binlog file name used in case of \"specific-offset\" startup mode");

    public static final Option<Long> SCAN_STARTUP_SPECIFIC_OFFSET_POS = Options.key("scan.startup.specific-offset.pos")
            .longType()
            .noDefaultValue()
            .withDescription("Optional binlog file position used in case of \"specific-offset\" startup mode");

    public static final Option<String> SCAN_STARTUP_SPECIFIC_OFFSET_GTID_SET = Options.key(
                    "scan.startup.specific-offset.gtid-set")
            .stringType()
            .noDefaultValue()
            .withDescription("Optional GTID set used in case of \"specific-offset\" startup mode");

    public static final Option<Long> SCAN_STARTUP_SPECIFIC_OFFSET_SKIP_EVENTS = Options.key(
                    "scan.startup.specific-offset.skip-events")
            .longType()
            .noDefaultValue()
            .withDescription("Optional number of events to skip after the specific starting offset");

    public static final Option<Long> SCAN_STARTUP_SPECIFIC_OFFSET_SKIP_ROWS = Options.key(
                    "scan.startup.specific-offset.skip-rows")
            .longType()
            .noDefaultValue()
            .withDescription("Optional number of rows to skip after the specific offset");

    public static final Option<Long> SCAN_STARTUP_TIMESTAMP_MILLIS = Options.key("scan.startup.timestamp-millis")
            .longType()
            .noDefaultValue()
            .withDescription("Optional timestamp used in case of \"timestamp\" startup mode");

    public static final Option<Duration> HEARTBEAT_INTERVAL = Options.key("heartbeat.interval")
            .durationType()
            .defaultValue(Duration.ofSeconds(30))
            .withDescription(
                    "Optional interval of sending heartbeat event for tracing the latest available binlog offsets");

    public static final Option<Boolean> SCAN_READ_CHANGELOG_AS_APPEND_ONLY_ENABLED = Options.key(
                    "scan.read-changelog-as-append-only.enabled")
            .booleanType()
            .defaultValue(false)
            .withDescription("Whether to convert the changelog data stream to an append-only data stream");

    // ----------------------------------------------------------------------------
    // experimental options, won't add them to documentation
    // ----------------------------------------------------------------------------
    public static final Option<Integer> CHUNK_META_GROUP_SIZE = Options.key("chunk-meta.group.size")
            .intType()
            .defaultValue(1000)
            .withDescription(
                    "The group size of chunk meta, if the meta size exceeds the group size, the meta will be divided into multiple groups.");
    public static final Option<Double> CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_UPPER_BOUND = Options.key(
                    "chunk-key.even-distribution.factor.upper-bound")
            .doubleType()
            .defaultValue(1000.0d)
            .withDescription(
                    "The upper bound of chunk key distribution factor. The distribution factor is used to determine whether the"
                            + " table is evenly distribution or not."
                            + " The table chunks would use evenly calculation optimization when the data distribution is even,"
                            + " and the query MySQL for splitting would happen when it is uneven."
                            + " The distribution factor could be calculated by (MAX(id) - MIN(id) + 1) / rowCount.");
    public static final Option<Double> CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_LOWER_BOUND = Options.key(
                    "chunk-key.even-distribution.factor.lower-bound")
            .doubleType()
            .defaultValue(0.05d)
            .withDescription(
                    "The lower bound of chunk key distribution factor. The distribution factor is used to determine whether the"
                            + " table is evenly distribution or not."
                            + " The table chunks would use evenly calculation optimization when the data distribution is even,"
                            + " and the query MySQL for splitting would happen when it is uneven."
                            + " The distribution factor could be calculated by (MAX(id) - MIN(id) + 1) / rowCount.");
    public static final Option<Boolean> SCAN_NEWLY_ADDED_TABLE_ENABLED = Options.key("scan.newly-added-table.enabled")
            .booleanType()
            .defaultValue(false)
            .withDescription(
                    "Discovers newly matching tables during INITIAL streaming and restores their snapshot progress from checkpoints.");
    public static final Option<Boolean> SCAN_INCREMENTAL_SNAPSHOT_METADATA_RELEASE_ENABLED = Options.key(
                    "scan.incremental.snapshot.metadata.release.enabled")
            .booleanType()
            .defaultValue(false)
            .withDescription("Whether to release the snapshot split metadata (assigned splits, finished"
                    + " offsets and table schemas) held by the source coordinator once the"
                    + " source has entered the binlog phase, to reduce JobManager memory"
                    + " pressure on jobs with a very large number of snapshot splits. Disabled"
                    + " by default. The release happens only after a checkpoint completes, so it has no"
                    + " effect without checkpointing. Cannot be enabled together with"
                    + " scan.newly-added-table.enabled (the job fails to start), since that"
                    + " flow needs the metadata that would be released.");
    public static final Option<String> SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN = Options.key(
                    "scan.incremental.snapshot.chunk.key-column")
            .stringType()
            .noDefaultValue()
            .withDescription(
                    "The chunk key of table snapshot, captured tables are split into multiple chunks by a chunk key when read the snapshot of table."
                            + "By default, the chunk key is the first column of the primary key.");
    public static final Option<Boolean> SCAN_INCREMENTAL_CLOSE_IDLE_READER_ENABLED = Options.key(
                    "scan.incremental.close-idle-reader.enabled")
            .booleanType()
            .defaultValue(false)
            .withDescription("Whether to close idle readers at the end of the snapshot phase. This feature depends on "
                    + "FLIP-147: Support Checkpoints After Tasks Finished. The flink version is required to be "
                    + "greater than or equal to 1.14 when enabling this feature.");
    public static final Option<Boolean> SCAN_INCREMENTAL_SNAPSHOT_BACKFILL_SKIP = Options.key(
                    "scan.incremental.snapshot.backfill.skip")
            .booleanType()
            .defaultValue(false)
            .withDescription(
                    "Whether to skip backfill in snapshot reading phase. If backfill is skipped, changes on captured tables during snapshot phase will be consumed later in binlog reading phase instead of being merged into the snapshot. WARNING: Skipping backfill might lead to data inconsistency because some binlog events happened within the snapshot phase might be replayed (only at-least-once semantic is promised). For example updating an already updated value in snapshot, or deleting an already deleted entry in snapshot. These replayed binlog events should be handled specially.");
    public static final Option<Boolean> PARSE_ONLINE_SCHEMA_CHANGES = Options.key(
                    "scan.parse.online.schema.changes.enabled")
            .booleanType()
            .defaultValue(false)
            .withDescription(
                    "Whether to parse schema change events generated by gh-ost/pt-osc utilities. Defaults to false.");
    public static final Option<Boolean> USE_LEGACY_JSON_FORMAT = Options.key("use.legacy.json.format")
            .booleanType()
            .defaultValue(true)
            .withDescription(
                    "Whether to use legacy json format. The default value is true, which means there is no whitespace before value and after comma in json format.");

    public static final Option<Boolean> SCAN_INCREMENTAL_SNAPSHOT_UNBOUNDED_CHUNK_FIRST = Options.key(
                    "scan.incremental.snapshot.unbounded-chunk-first.enabled")
            .booleanType()
            .defaultValue(true)
            .withDescription(
                    "Whether to assign the unbounded chunks first during snapshot reading phase. This might help reduce the risk of the TaskManager experiencing an out-of-memory (OOM) error when taking a snapshot of the largest unbounded chunk.");
    // YakFlow's runtime-only settings are not part of the persisted job definition.
    public static final Option<String> TOPIC_PREFIX =
            Options.key("connector.mysql-cdc.topic-prefix").stringType().noDefaultValue();

    public static final Option<Integer> QUEUE_CAPACITY =
            Options.key("data-sync.mysql-cdc.queue-capacity").intType().defaultValue(256);
}
