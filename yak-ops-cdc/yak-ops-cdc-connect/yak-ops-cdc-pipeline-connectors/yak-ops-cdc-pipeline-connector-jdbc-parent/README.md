# JDBC Pipeline Sink

The JDBC pipeline sink follows Flink CDC's `DataSink` / `MetadataApplier` contract
and reuses Flink JDBC Connector 3.3.0-1.20 for statement execution, batching,
flush and retry. The first database-specific implementation is MySQL.

## Modules

- `yak-ops-cdc-pipeline-connector-jdbc-core`: CDC Sink V2 adapter, table writer,
  JDBC dialect SPI, catalog API, output-format builder and row converter.
- `yak-ops-cdc-pipeline-connector-jdbc-mysql`: MySQL dialect, catalog, type mapping,
  SQL generation and dialect provider.
- `yak-ops-cdc-pipeline-connector-jdbc`: shaded connector assembly.

## Execution model

```text
DataChangeEvent -> JdbcWriter -> JdbcTableWriter
                                  -> JdbcOutputFormatBuilder
                                  -> JdbcRowConverter / JdbcBatchStatementExecutor
                                  -> Flink JdbcOutputFormat
SchemaChangeEvent -> JdbcMetadataApplier -> JdbcCatalog -> JdbcDialect (DDL SQL)
```

- `JdbcWriter` coordinates table-scoped writers and flushes.
- Each table-scoped writer owns its Flink JDBC output format and a dedicated
  `SimpleJdbcConnectionProvider` when constructed from normal JDBC connection
  options. Schema changes close and rebuild only that table's format.
- `JdbcDialect` generates SQL but does not open connections. `JdbcCatalog`
  owns metadata lookup and the DDL execution connection.
- `JdbcRowConverter` binds typed CDC row values to JDBC statement parameters.
- This sink currently provides **at-least-once** semantics, not XA exactly-once.
  The existing CDC event reduction and unkeyed-table behavior are unchanged.

## Running

Example: [mysql-to-mysql.yaml](examples/mysql-to-mysql.yaml).
The target database must exist before first execution. Target table creation is
handled by `JdbcMetadataApplier` when a `CreateTableEvent` is applied and the
database user has DDL permissions. Existing target tables are not automatically
rewritten by `CREATE TABLE IF NOT EXISTS`.

Build from repository root (without running tests):

```bash
bash mvnw -B -ntp -pl yak-ops-cdc/yak-ops-cdc-connect/yak-ops-cdc-pipeline-connectors/yak-ops-cdc-pipeline-connector-jdbc-parent/yak-ops-cdc-pipeline-connector-jdbc -am -DskipTests compile
```

The CDC connector is based on the JDBC Pipeline Sink from Apache Flink CDC
[PR #3916](https://github.com/apache/flink-cdc/pull/3916), with Yak Ops
changes for Flink 1.20.3 and the refactored writer architecture.
