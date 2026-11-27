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

The reader performs JDBC I/O on a background fetch thread; Flink's `pollNext` only drains a bounded event queue. Reader checkpoints contain the active split's last emitted numeric key and pending splits. Enumerator checkpoints contain unassigned splits and the split-discovery completion state.

Recovery is split-level and At-Least-Once. A split without a numeric primary key is replayed from its beginning after failure, so a target should use idempotent writes where duplicate rows are not acceptable.

Independent JDBC connections do not share a global snapshot by default. Concurrent source-table updates can therefore be observed at different times by different splits. V1 does not claim cross-split snapshot consistency.

## Deliberate V1 exclusions

- CDC, binlog/WAL, incremental polling and streaming runtime mode.
- Database-wide wildcard discovery and automatic future-table inclusion.
- Custom SQL, joins, subqueries and SQL-based split rewriting.
- String/composite-key range splitting and database-specific snapshot export.
- A separate transform or field-mapping configuration.
