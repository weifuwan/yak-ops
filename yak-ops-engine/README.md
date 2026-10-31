# Yak Ops Engine

The engine uses Apache Flink CDC `release-3.7.0` Pipeline definitions and translator boundaries to compose an embedded YakFlow execution.

## Definition and execution flow

The Task API accepts JSON with explicit Connector factory identifiers. The Task plugin converts that JSON into Pipeline YAML. `CliFrontend` reads a local YAML file, resolves its explicit connection settings and freezes the selected source tables. Both entrypoints use `YamlPipelineDefinitionParser`, `PipelineDef` and `YakFlowPipelineComposer`. The Composer resolves admitted metadata, pins resource claims, and constructs the Event graph through source, transform, schema, partitioning, and sink translators.

| Package | Responsibility |
| --- | --- |
| `io.yak.ops.cli` | Standalone command line, explicit connection admission, table discovery, and execution lifetime |
| `io.yak.ops.cli.parser`, `io.yak.ops.cli.utils` | YAML parsing, configuration flattening, and `PipelineDef` creation |
| `io.yak.ops.composer.definition` | Source, sink, route, transform, and Pipeline options; authorized table bindings |
| `io.yak.ops.composer` | Composition, admission, trusted submission context, and deferred execution contracts |
| `io.yak.ops.composer.validation` | Connector-owned capability validation and authorized schema checks |
| `io.yak.ops.composer.yakflow` | Direct graph composition and YakFlow execution configuration |
| `io.yak.ops.composer.yakflow.translator` | Source, transform, schema/routing, partitioning, and sink translation |
| `io.yak.ops.composer.execution` | Initial schema preparation, incremental batch cursors, and execution progress |

Source and sink factories are discovered independently through their SPIs. Identifiers include `mysql` for CDC and `jdbc-mysql`, `jdbc-postgres`, and `jdbc-oracle` for JDBC. Product display categories do not choose the graph. Connector factories own datasource support, schema requirements, boundedness, and execution-specific validation.

Task execution is deferred until its Instance is durably registered; the standalone CLI creates a local execution identity. Resource revisions are revalidated before side effects. YakFlow retains physical job ownership, cancellation, mailbox execution, checkpoint coordination, and source/sink commit lifecycles. The engine does not parse the product JSON or own the Task database.

## Standalone CLI

Use Java 21 and build the engine with its reactor dependencies before launching the entrypoint. The first command installs the local artifacts needed by the second command; `exec:java` is deliberately run only for the engine module.

```bash
bash mvnw -pl yak-ops-engine -am -DskipTests install
bash mvnw -pl yak-ops-engine exec:java \
  -Dexec.args="--runtime-mode BATCH --state-dir /absolute/path/state /absolute/path/pipeline.yaml"
```

`--runtime-mode BATCH|STREAMING` overrides `pipeline.execution.runtime-mode`. `--state-dir` is optional; without it the CLI creates a temporary local state root. Each invocation prints its unique execution ID and checkpoint subdirectory. State remains available after the process exits. A new invocation starts a new execution; the CLI does not yet resume a previous invocation's identity. Runtime recovery within that execution uses the same checkpoint path and restart configuration as embedded YakFlow jobs.

The CLI waits for terminal execution. Ctrl-C requests cancellation and waits for physical termination, including schema preparation and connector cleanup. Exit codes are `0` for successful completion, `130` for cancellation, and `1` for failure. It does not look up platform Workspaces or saved datasource IDs: credentials must come from the supplied YAML, and `datasource-id` is rejected at this entrypoint. Keep the YAML file's access permissions appropriate for its credentials.

This bounded MySQL snapshot example creates or reconciles its target table and then writes the selected rows:

```yaml
source:
  type: mysql
  hostname: 127.0.0.1
  port: 3306
  username: cdc_reader
  password: "<source-password>"
  tables: inventory.products
  scan.startup.mode: snapshot
sink:
  type: jdbc-mysql
  hostname: 127.0.0.1
  port: 3307
  username: writer
  password: "<target-password>"
  write.mode: UPSERT
  yak.schema.initial: CREATE_OR_ALTER
route:
  - source-table: inventory.products
    sink-table: warehouse.products_copy
pipeline:
  name: products-copy
  execution.runtime-mode: BATCH
  local-time-zone: UTC
```

The source database and target database must already exist. To use an ongoing MySQL CDC pipeline, set `scan.startup.mode: initial` and `execution.runtime-mode: STREAMING`; omit the command's BATCH override or change it to STREAMING. Schema evolution additionally requires `pipeline.schema.change.behavior: evolve`. For a full JDBC copy, use `source.type: jdbc-mysql`, omit the CDC startup option, and keep BATCH mode. Bounded JDBC cursor batches require the platform's durable Task cursor store and are explicitly rejected by the standalone CLI.

MySQL `hostname`, `port`, `username`, and `password` are mapped into the JDBC connection contract; the default port is `3306`. MySQL endpoints can instead supply `connection.url`. JDBC PostgreSQL and Oracle endpoints use their corresponding `connection.url` and `username`/`password`, or the explicit `connection.username`/`connection.password` keys. Supplying both forms of a credential or mixing `hostname` with `connection.url` is rejected. Other supported `connection.*` and connector options are validated against the selected factory. MySQL replication server ID and topic prefix are allocated from the CLI execution identity; user overrides are rejected.

Source `tables` and optional `tables.exclude` use the Flink CDC `Selectors` implementation. For example, `tables: inventory.products(_archive)?` together with `tables.exclude: inventory.products_archive` selects only `inventory.products`. Discovery includes physical tables, excludes MySQL system databases, and freezes at most 100 matches before composition. New tables are not added while a job runs. Routes identify complete target namespaces: `database.table` for MySQL, `database.schema.table` for PostgreSQL, and `schema.table` for Oracle. The Composer requires ordinary alphanumeric/underscore/dollar identifier parts; names containing literal dots or other punctuation are rejected.

## Supported execution boundary

- BATCH requires a bounded source. JDBC and MySQL CDC snapshot-only definitions are bounded; ongoing MySQL CDC definitions use STREAMING.
- Pipeline parallelism overrides obey the engine's `1..16` execution limits. Source-specific constraints are checked before target preparation; stream-only MySQL CDC requires one source reader.
- Composition receives explicit source tables. Task definitions supply that selection directly; CLI selectors are expanded into an immutable selection before admission. Flink CDC route patterns and replacement symbols resolve those tables into one-to-one bindings. One-to-many routing and many-to-one schema merging are rejected.
- Projection and filter expressions use Flink CDC's SQL parser and expression processors. Schema routing, schema-event propagation, partitioning, and sink commit stages operate on Events.
- Projected primary keys must directly map non-null physical columns from a complete source primary key or an independently verified source unique key. A complete renamed key requires explicit `primary-keys`; computed keys and partial composite keys are rejected.
- The engine supports regular metadata sources. Native Event sources that emit schema changes require one source reader; built-in MySQL CDC retains its existing concurrency support. Parallel-metadata sources (`isParallelMetadataSource=true`) and distributed schema coordination are rejected. Ordinary data pipelines can use multiple source and sink subtasks.
- Unsupported UDF/model definitions, transform partition-key/table-option overrides, post-transform converters, and unknown options fail validation. They are not silently discarded.
- Distributed Flink cluster launch and deployment are outside the embedded YakFlow execution contract.

The current Task JSON contract and an example are documented in [DATA_SYNC Pipeline definitions](../docs/data-sync-pipeline.md). Compilation, unit tests, database integration, and process-recovery suites are distinct checks; see [DEVELOPMENT.md](../DEVELOPMENT.md) for their execution requirements.
