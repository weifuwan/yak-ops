# Yak Ops JDBC Source

The JDBC source is a bounded Flink Source for offline full-table reads. It is intentionally separate from the MySQL and PostgreSQL CDC sources: it reads existing rows and does not tail binlog/WAL or produce ongoing updates.

## Pipeline configuration

```yaml
source:
  type: jdbc
  url: jdbc:postgresql://localhost:5432/app
  driver-name: org.postgresql.Driver
  username: app
  password: ${secret}
  tables: public.orders,public.customers
  scan.fetch-size: 1000
  scan.split-size: 100000

pipeline:
  execution.runtime-mode: BATCH
  parallelism: 4
```

Yak Ops task definitions do not persist inline connection credentials. The data-sync task resolves the datasource ID and injects the JDBC URL, driver class, username and password only when building the executable Pipeline.

## V1 behavior

- Reads explicit one-table or multi-table selections.
- Uses JDBC DatabaseMetaData for columns, primary keys and table discovery.
- A single numeric primary key is range-split using MIN/MAX/COUNT. The maximum number of splits is bounded by four times the configured Pipeline parallelism.
- Tables without a single numeric primary key use one split.
- `scan.fetch-size` defaults to 1000; `scan.split-size` defaults to 100000.
- Rows are emitted as INSERT DataChangeEvents. Existing Pipeline Route, Transform and JDBC Sink behavior is reused.
- Field projection, filtering and primary-key declarations remain in the existing `transform` configuration. The source does not introduce another field-mapping model.

## Recovery and consistency

The reader uses Flink's `SingleThreadMultiplexSourceReaderBase` and connector-base split fetcher. JDBC I/O runs on the fetcher thread, while the mailbox thread emits bounded batches and updates the active split's last emitted numeric key. Reader checkpoints include active and queued splits; enumerator checkpoints include unassigned splits and split-discovery completion state.

New checkpoints use explicit version-2 serializers for splits and enumerator state. Version-1 Java-serialized split and enumerator payloads are still readable, so existing savepoints can be restored after the package refactor.

Recovery is split-level and At-Least-Once. A split without a numeric primary key is replayed from its beginning after failure, so a target should use idempotent writes where duplicate rows are not acceptable.

Independent JDBC connections do not share a global snapshot by default. Concurrent source-table updates can therefore be observed at different times by different splits. V1 does not claim cross-split snapshot consistency.

## Package responsibilities

- `config`: immutable source configuration.
- `connection`: JDBC driver and connection creation.
- `catalog`: metadata discovery and schema mapping.
- `dialect`: identifier quoting and catalog/schema naming rules.
- `split`: split model, range planning and split serialization.
- `enumerator`: asynchronous discovery, split assignment and enumerator state.
- `reader`: Flink reader lifecycle, JDBC split reads, record emission and progress state.

## Deliberate V1 exclusions

- CDC, binlog/WAL, incremental polling and streaming runtime mode.
- Database-wide wildcard discovery and automatic future-table inclusion.
- Custom SQL, joins, subqueries and SQL-based split rewriting.
- String/composite-key range splitting and database-specific snapshot export.
- A separate transform or field-mapping configuration.
