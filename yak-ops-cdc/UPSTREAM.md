# Flink CDC 3.7 source migration

Upstream: https://github.com/apache/flink-cdc/tree/release-3.7.0

Production source is copied from Apache Flink CDC 3.7.0 and keeps its original Java packages. Maven module names, local dependency coordinates and reactor module lists are adapted for Yak Ops.

The MySQL and PostgreSQL CDC source connectors, MySQL and PostgreSQL Pipeline sources, and Doris Pipeline sink connector are included with their required shared CDC modules. Other database connectors, Flink 2 compatibility, distribution, optional AI model client implementations, Python bindings and the E2E harness are not copied. The example-only Dummy Model and UDF modules and the Flink SQL MySQL CDC connector packaging module were removed; the MySQL and PostgreSQL Pipeline sources and JDBC sink remain.

Baseline: Flink 1.20.3, Debezium 1.9.8.Final. Yak Ops application/task integration intentionally deferred.

The CLI runs with a local Flink MiniCluster only. Remote, YARN, and Kubernetes deployment code is removed. Flink home is optional; `--flink-home` or `FLINK_HOME` can supply a Flink configuration directory. Any non-local `--target` or `execution.target` is rejected.

Build the CLI, MySQL/PostgreSQL sources, MySQL/PostgreSQL Pipeline sources, Doris sink and JDBC sink from the repository root:

```sh
bash mvnw -pl :yak-ops-cdc-cli,:yak-ops-connector-mysql-cdc,:yak-ops-connector-postgres-cdc,:yak-ops-cdc-pipeline-connector-mysql,:yak-ops-cdc-pipeline-connector-postgres,:yak-ops-cdc-pipeline-connector-doris,:yak-ops-cdc-pipeline-connector-jdbc -am clean package
```

JDBC Pipeline Sink is separately ported from the unmerged Apache Flink CDC PR #3916
(`d8bf6f37001bd67b48174456a57e484d9078f040`). The JDBC connector dependency
uses JDBC `3.3.0-1.20` to match Yak Ops Flink 1.20.3, with Maven coordinates remapped to Yak Ops.
The original PR uses JDBC `3.2.0-1.19`.


The Doris Pipeline sink connector module is copied from Apache Flink CDC `release-3.7.0` (upstream commit `045e8da45df4b1017849b04e2d8bfe8f0ed7936d`), from `flink-cdc-connect/flink-cdc-pipeline-connectors/flink-cdc-pipeline-connector-doris`. Its original Java packages, service registration, production classes and tests are retained. Maven coordinates are remapped to Yak Ops; the Doris Flink connector dependency remains `25.1.0` for Flink `1.20`, matching the upstream release tag. The single JUnit 4/5 bridge helper `ExternalResourceProxy` is copied into this module's test sources instead of introducing the unrelated upstream test-utility module.


## PostgreSQL CDC source and Pipeline connectors

Both PostgreSQL connector modules are copied from Apache Flink CDC `release-3.7.0` (upstream commit `045e8da45df4b1017849b04e2d8bfe8f0ed7936d`):

- Source connector: `flink-cdc-connect/flink-cdc-source-connectors/flink-connector-postgres-cdc` → `yak-ops-connector-postgres-cdc`.
- Pipeline connector: `flink-cdc-connect/flink-cdc-pipeline-connectors/flink-cdc-pipeline-connector-postgres` → `yak-ops-cdc-pipeline-connector-postgres`.

The original Java packages, service registrations, production sources, tests and test resources are retained. Maven coordinates are remapped to Yak Ops. The PostgreSQL JDBC driver is pinned to `42.7.3`, JTS remains `1.19.0`, and Flink/Debezium align with the existing Yak Ops CDC baseline (`1.20.3` / `1.9.8.Final`). The PostgreSQL source test sources include the required upstream test helpers (`AssertUtils`, `ExternalResourceProxy`, `StaticExternalResourceProxy`, and `TestSourceContext`) plus the two Flink 1 compatibility test adapters. They are kept test-scoped within this connector rather than adding separate upstream test-utility or compatibility modules.
