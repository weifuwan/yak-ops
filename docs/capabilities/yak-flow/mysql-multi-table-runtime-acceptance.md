# YakFlow MySQL Multi-Table Runtime Acceptance

Status: Opt-in engine integration tests. A workflow definition or committed test does **not**
mean its real-database runs have passed. Product Task / Controller / Service / DAO and UI are
out of scope until the new Runtime is connected to the business execution entrypoint.

## Execution boundary

Both paths use the production Core `TableRecord` contract and the embedded Runtime:
`SourceTransformation → StreamGraphGenerator → StreamingJobGraphGenerator → ExecutionGraph
→ StreamTask → SinkTransformation → JdbcSink / JdbcOutputFormat`. No test-specific runner,
second table router, mock Source, or direct-to-target shortcut is used for the snapshot.

| Acceptance | Source | Target | Main assertions |
| --- | --- | --- | --- |
| `JdbcSinkCrossDatabaseIT` | One bounded `JdbcSource` with two distinct schemas, 2 readers | One `JdbcSink`, 1 writer | Full target primary-key and field sets after snapshot, changelog mutations and idempotent writer replay |
| `MySqlCdcRuntimeCrossDatabaseIT` | One Hybrid CDC Source with two distinct schemas, 2 snapshot readers and one Binlog owner | One `JdbcSink`, 1 writer | Initial Snapshot, INSERT / UPDATE / DELETE / primary-key relocation, completed Checkpoint, cancel / restore and post-restore data convergence |
| `MySqlHybridIT` | Real MySQL Snapshot + Binlog, deterministic connector harness | Collected Core `TableRecord` events | Both tables change after the Low Watermark and before Snapshot split consumption; no Binlog replay until a completed handoff Checkpoint |

The JDBC acceptance uses different source/target column names and a third column on
one route. The CDC acceptance uses `orders(id, name)` and
`items(id, sku, qty)` with distinct `TableSchema` values.
Both compare the complete target row contents, not just row counts. Tests fail if a
target primary key occurs twice. All target tables must already exist.

## Run

Actions → **JDBC Sink Cross-Database Acceptance** → **Run workflow**.
Inspect the `mysql-to-mysql` job: its MySQL 8 source and target are separate disposable
containers on localhost ports `3306` and `3307`. The workflow also retains
MySQL → PostgreSQL, PostgreSQL → Oracle and Oracle → MySQL cases.
To run only the MySQL → MySQL case locally, provide two disposable MySQL databases
and run:

```bash
bash mvnw -B -ntp -pl yak-flow/yak-flow-connector-jdbc -am \
  -Dtest=JdbcSinkCrossDatabaseIT -Dsurefire.failIfNoSpecifiedTests=false \
  -Djdbc.it.allow-write=true \
  '-Djdbc.it.source.url=jdbc:mysql://127.0.0.1:3306/yak_it?useSSL=false&allowPublicKeyRetrieval=true' \
  -Djdbc.it.source.user=yak -Djdbc.it.source.password=yakpass \
  '-Djdbc.it.target.url=jdbc:mysql://127.0.0.1:3307/yak_it?useSSL=false&allowPublicKeyRetrieval=true' \
  -Djdbc.it.target.user=yak -Djdbc.it.target.password=yakpass test
```

Actions → **MySQL CDC Runtime Cross-Database Acceptance** → **Run workflow**.
The MySQL target job additionally executes `MySqlHybridIT` once, with a controlled
two-table Snapshot / Binlog race. The CDC workflow still checks MySQL,
PostgreSQL and Oracle targets. The MySQL source requires ROW Binlog,
`binlog-row-image=FULL`, unique replication server IDs and retained Binlog.
For connection parameters, local commands and restore assumptions see
[MySQL CDC Runtime + JDBC Acceptance](mysql-cdc-runtime-jdbc-acceptance.md).

Both workflows are **manual only** and do not add jobs to normal pull-request CI.
The integration tests fail explicitly when their opt-in properties or database
connections are missing. Each test creates and removes its own tables in
disposable databases; the workflows emit database logs on failure.
Never run the destructive fixtures against non-disposable schemas.

## Semantics

- Bounded JDBC snapshot does not guarantee one atomic read-time snapshot across tables.
- The JDBC test verifies a post-job `JdbcWriter` changelog/replay path; it does
  **not** claim a JDBC Runtime checkpoint/restore test.
- CDC uses stable Source/Sink UIDs and `RESTORE_LATEST` only after a completed
  durable checkpoint. Current Hybrid Snapshot supports single-column non-null
  BIGINT primary keys and a conservative global Low/High replay, not Flink CDC's
  per-chunk normalization.
- Changelog UPDATE_BEFORE/UPDATE_AFTER must stay adjacent on the sole Sink
  subtask. The tests do not validate parallel Sink writers, XA, exactly-once,
  schema evolution, dynamic table discovery or the product-facing run API.
