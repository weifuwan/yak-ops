# JDBC Pipeline Sink (Flink CDC PR #3916 port)

The JDBC Core and MySQL Dialect implementations are copied from the unmerged
[Apache Flink CDC PR #3916](https://github.com/apache/flink-cdc/pull/3916),
upstream commit `d8bf6f37001bd67b48174456a57e484d9078f040`.
Java packages, code structure and tests are retained. Yak Ops-specific changes are limited to Maven
coordinates and Flink 1.20 JDBC dependency alignment, plus the sink-parallelism change
from that upstream PR.

Modules:
- `yak-ops-cdc-pipeline-connector-jdbc-core`: JDBC Sink factory, writer, dialect SPI, schema metadata.
- `yak-ops-cdc-pipeline-connector-jdbc-mysql`: MySQL SQL and column type dialect.
- `yak-ops-cdc-pipeline-connector-jdbc`: shaded connector and upstream integration tests.

Example: [mysql-to-mysql.yaml](examples/mysql-to-mysql.yaml).
The target database must exist before first execution. Table creation is handled by
`JdbcMetadataApplier` when `CreateTableEvent` is received, provided the database user has
DDL permissions. The JDBC sink currently has **at-least-once**, not exactly-once, semantics.
Use a dedicated MySQL account for the source with replication privileges and ensure
row-based binlogging is enabled. Change the sample credentials before use.

Compile from repository root:

```bash
bash mvnw -B -ntp -pl yak-ops-cdc/yak-ops-cdc-connect/yak-ops-cdc-pipeline-connectors/yak-ops-cdc-pipeline-connector-jdbc-parent/yak-ops-cdc-pipeline-connector-jdbc -am -DskipTests compile
```
