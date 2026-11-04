# Yak Ops

Yak Ops maintains three independently evolving workspaces:

- [yak-ops-ui](yak-ops-ui/) — the existing React frontend, unchanged by the scheduler import.
- [yak-ops-cdc](yak-ops-cdc/) — Flink CDC 1.20.3 pipeline CLI, runtime, MySQL CDC source and JDBC sink.
- [yak-ops-scheduler](yak-ops-scheduler/) — source-preserving migration of Apache DolphinScheduler 3.4.3-release.

## DolphinScheduler import status

The Scheduler source baseline is [apache/dolphinscheduler commit 26228f9c](https://github.com/apache/dolphinscheduler/commit/26228f9c5bff24b3ded7095484caf7a5ecffa050), on the 3.4.3-release branch. Its upstream POM declares 3.4.4-SNAPSHOT; the imported Maven coordinates are io.yak.ops:yak-ops-*:1.4.0.

Core Master, Worker, Task Executor, Workflow Engine, DAO, Service, SPI, Registry, Scheduler, EventBus, Extract, Alert, Authentication, API and Standalone source trees have been imported without changing Java packages or source bodies. Tests and resources are retained. The separate DolphinScheduler UI, distribution and optional big-data-specific plugins are not included in the active source tree.

See [the full manifest](yak-ops-scheduler/MIGRATION_MANIFEST.json) for each imported source path, its original SHA-256, and exclusions, and [the migration notes](yak-ops-scheduler/UPSTREAM.md) for provenance. Most source files remain upstream-identical; Maven metadata and one legacy StAX dependency exclusion were adapted. Separate CI now verifies all 99 imported Maven modules compile, a basic H2-backed Workflow executes, and Standalone starts with H2 and reports health UP.

## Local development

Java 21 and the checked-in Maven wrapper are used for the existing Yak Ops reactor. The migrated Scheduler subtree retains the upstream Java 8 / Spring Boot 2.x conventions and has been compiled and started under JDK 11 in CI. It is still not integrated with the separate Flink CDC runtime.

```sh
# Inspect newly imported Scheduler Maven reactor
bash mvnw -B -ntp -pl :yak-ops-scheduler validate

# With JDK 11: full Scheduler source compile and standalone H2 startup
# bash mvnw -B -ntp -f yak-ops-scheduler/pom.xml -Dmaven.test.skip=true -Dspotless.skip=true -Djacoco.skip=true -Danalyze.skip=true compile
# bash scripts/run-scheduler-standalone.sh

# Build CDC CLI and runtime dependencies without tests
bash mvnw -B -ntp -pl :yak-ops-cdc-cli -am -DskipTests package

# Frontend development
cd yak-ops-ui
npm ci
npm run dev
```

At present, the frontend is not connected to a functioning migrated Scheduler API or CDC task execution endpoint. Scheduler-to-CDC integration is planned separately.

See [DEVELOPMENT.md](DEVELOPMENT.md), [AGENTS.md](AGENTS.md), and [the contributor guide](.github/CONTRIBUTING.md).
