# YakFlow MySQL CDC — PR1 Binlog Source

Status: Stream-only connector foundation. PR2 adds a separate Hybrid Snapshot mode; product runtime integration is still pending.

## Implementation

- `MySqlCdcSource` implements the Core `Source<TableRecord, MySqlBinlogSplit, MySqlPendingSplitsState>` contract and declares `CONTINUOUS_UNBOUNDED`.
- `MySqlSourceEnumerator` assigns exactly one unbounded Binlog split to one SourceReader. Parallel Snapshot is PR2 work; PR1 intentionally rejects Source parallelism greater than one.
- `MySqlSourceReader` extends Connector Base's `SingleThreadMultiplexSourceReaderBase`. A Debezium engine executes off-mailbox, yields **bounded** `BinlogEvent` batches, and the existing mailbox `RecordEmitter` advances the last-emitted offset only after delivering all rows.
- One Debezium UPDATE produces one `BinlogEvent` containing adjacent `UPDATE_BEFORE` and `UPDATE_AFTER` rows. The Runtime checkpoint barrier cannot split these two emissions because they run in the same mailbox call. INSERT, DELETE and heartbeat records share the ordered handover.
- Only a completed YakFlow checkpoint may seed a new Debezium attempt. The attempt-local Kafka Connect offset backing store is deliberately **volatile**; engine-prefetched offset commits are never treated as reader-delivered progress.
- Split serializer persists Kafka Connect's partition and fine-grained offset fields (including event and row positions) plus Debezium **Schema History** bytes. The history and source fingerprint are validated on restore. No temporary local offset file is used as a recovery authority; history files are attempt-local and deleted on close.
- All stream records must match a frozen table schema. Unsupported source types and incompatible changes fail instead of being silently converted. Only common resolved MySQL logical types are supported.

## Starting a stream-only source

```java
MySqlCdcSource source = MySqlCdcSource.builder()
        .hostname("127.0.0.1")
        .port(3306)
        .username("replication_user")
        .password(password)
        .serverId(5401)
        .topicPrefix("yak_cdc_orders")
        .table(new TableId("shop", null, "orders"), resolvedTableSchema)
        .build();
```

This requires an explicit, already resolved ordered `TableSchema`. PR1 **does not** automatically discover tables, register product Tasks or create target tables. The builder performs no I/O.

The first start uses Debezium `snapshot.mode=no_data`: it snapshots **schema only**, then begins streaming from the current Binlog position. Existing table rows are not copied. An arbitrary historical/earliest offset without matching Schema History is deliberately unsupported; hybrid initial backfill belongs to PR2. A checkpoint before Debezium has a complete Schema History and a mailbox-delivered offset **fails closed**. It never silently resumes from a newer position.

MySQL requirements: enabled ROW Binlog, `binlog_row_image=FULL`, a distinct replication server ID, and sufficient SELECT, REPLICATION SLAVE/REPLICATION CLIENT permissions. Retain Binlog longer than expected downtime. Purged restart offsets cause an explicit failure, not a fresh-source reset.

## Scope and guarantees

- **At-least-once**, not Exactly-once. JDBC Sink commits are independent of Source offset durability.
- This stream-only Source does not perform a full snapshot. For BIGINT-key initial snapshot + global Binlog replay, use [MySQL Hybrid Snapshot](mysql-cdc-hybrid.md).
- No DDL/Schema Evolution, rescale, multiple active Binlog readers, or product execution wiring.
- The source carries split metadata and Schema History under an 8 MiB serialized size limit. Schema changes during an active stream are not a supported operation; pause or reject such changes until evolution contracts exist.
- MySQL JDBC Source and JDBC Sink remain in their existing Connector modules. Debezium-specific state does not move to Core or Runtime.

## Verification

Run standard Unit/Format tests with `bash mvnw -pl yak-flow/yak-flow-connector-cdc-mysql -am verify`.

Opt-in integration: Actions → **MySQL CDC Binlog Acceptance** → **Run workflow**. It starts a disposable MySQL 8 with ROW/FULL logging and verifies multi-table INSERT, restart from serialized offset and Schema History, UPDATE pair and DELETE. This workflow is **not triggered on pull requests**. It does not replace PR3 Runtime→JDBC Sink cross-database or release acceptance.
