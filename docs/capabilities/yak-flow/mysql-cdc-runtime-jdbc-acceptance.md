# YakFlow MySQL CDC — PR3 Runtime + JDBC Cross-Database Acceptance

Status: Engine integration test and opt-in workflow added. **The three real database jobs must pass before claiming cross-database acceptance.** Product Task/Controller/Service/DAO and UI execution are not connected.

## Execution boundary

The existing Core `TableRecord` contracts connect MySQL CDC to JDBC Sink without another runner:

```text
MySqlHybridCdcSource (2 Source subtasks, Snapshot chunks + one Binlog owner)
  -> SourceTransformation<TableRecord>
  -> StreamGraphGenerator / StreamingJobGraphGenerator
  -> ExecutionGraph / SourceCoordinator / StreamTask / Checkpoint
  -> SinkTransformation<TableRecord>
  -> JdbcSink (1 Sink subtask, multi-table native UPSERT)
  -> JdbcWriter / JdbcOutputFormat / dialect statements
```

The JDBC Sink's UPDATE_BEFORE / UPDATE_AFTER contract requires adjacent events for the same source table on one Sink subtask. The acceptance graph therefore pins **Sink parallelism to one**, even when Snapshot Source parallelism is two. The Binlog split remains on Source subtask zero; routing two Binlog writers or parallel Sink subtasks without an ordering contract is not supported.

All source tables require a complete, frozen schema with exactly one non-null BIGINT primary key. The Sink uses a distinct `JdbcTableWritePlan` for every source-to-target table, with `JdbcWriteMode.UPSERT` for INSERT/UPDATE/DELETE replay. The Runtime requires stable Source/Sink UIDs, periodic checkpoints and an explicit durable `CheckpointingOptions.STATE_DIRECTORY`; `RESTORE_LATEST` may only be enabled for a valid completed checkpoint of the same graph.

## Acceptance coverage

`MySqlCdcRuntimePlanTest` compiles a two-reader, one-writer pipeline and checks the streaming graph and durable Checkpoint requirement **without any database I/O**.

`MySqlCdcRuntimeCrossDatabaseIT` uses real MySQL 8 ROW Binlog and a real target database, executing through the existing embedded Runtime. It:

1. Creates two source tables and two independent target-table routes.
2. Verifies initial Snapshot rows reach the actual JDBC target.
3. Applies UPDATE, DELETE, INSERT and primary-key relocation while Binlog is active; compares **full target key/value sets**, not just row counts.
4. Forces a durable aligned checkpoint, cancels the Job, and starts a new ExecutionGraph with `RESTORE_LATEST=true` and the same stable UIDs.
5. Applies additional UPDATE/DELETE/INSERT after restart and verifies both tables converge again.

This test covers engine-level Snapshot-to-Binlog handoff, changelog writer integration and recovered progress. The separate `MySqlHybridIT` specifically injects concurrent changes during Snapshot and checks the checkpoint-gated handoff before replay; PR3 does not replace it.

## Run the real database matrix

Actions → **MySQL CDC Runtime Cross-Database Acceptance** → **Run workflow**:

| Job | Source | Target |
| --- | --- | --- |
| mysql | MySQL 8 ROW Binlog | MySQL 8 |
| postgres | MySQL 8 ROW Binlog | PostgreSQL 16 |
| oracle | MySQL 8 ROW Binlog | Oracle Free 23 |

The workflow creates disposable Docker services with fixed local integration-test credentials. It is **manual only**; it does not add database service jobs to ordinary PR checks. To run a single target locally, start disposable MySQL with `--log-bin`, `--binlog-format=ROW`, and `--binlog-row-image=FULL`, provision an empty target, then use:

```bash
bash mvnw -B -ntp -pl yak-flow/yak-flow-connector-cdc-mysql -am \
  -Dtest=MySqlCdcRuntimePlanTest,MySqlCdcRuntimeCrossDatabaseIT \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dmysql.cdc.runtime.it.enabled=true \
  -Dmysql.cdc.it.password=rootpass \
  '-Djdbc.it.target.url=jdbc:postgresql://127.0.0.1:5432/yak_cdc_it' \
  -Djdbc.it.target.user=yak -Djdbc.it.target.password=yakpass test
```

The Oracle and MySQL cases substitute their own target URL, user and password. The test refuses to start without the explicit enable property and target connection options.

## Semantics and non-goals

- **At-least-once**, with native UPSERT for idempotent replay. No XA, Committer or Exactly-once guarantee.
- PR2 uses **global Low/High watermarks and ordered replay**, not Flink CDC's per-Chunk Low/High normalization. Snapshot rows may be temporarily inconsistent before catch-up.
- Only one Binlog reader, a single JDBC Sink writer, one embedded JVM and stable parallelism. No network partitioning, key-group rescale or multi-Sink topology.
- No Schema Evolution, DDL capture, composite or non-BIGINT Snapshot keys, product Task/Controller/Service/DAO, UI wiring or automatic target-table creation.
- MySQL replication permissions and retention must allow Debezium to restore its completed offset; stale or purged Binlog fails rather than resetting silently.
- A passing automated test proves only its particular database/container versions and operations. It does not establish production-grade multi-database consistency.
