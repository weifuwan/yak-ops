# Yak Ops Scheduler

[简体中文](README_zh_CN.md)

Yak Ops Scheduler is a **source-based import of Apache DolphinScheduler 3.4.3-release**, not a new scheduler implementation or a CDC task integration. The imported Java packages and execution model remain upstream-oriented. It is developed independently from `yak-ops-cdc` and `yak-ops-ui`.

The historical import manifest retains paths from the former `yak-ops-scheduler/` directory.

## Source and scope

- Upstream: [apache/dolphinscheduler commit 26228f9c](https://github.com/apache/dolphinscheduler/commit/26228f9c5bff24b3ded7095484caf7a5ecffa050) on `3.4.3-release`.
- Imported Maven coordinates: `io.yak.ops:yak-ops-*:1.4.0` (upstream POM uses `3.4.4-SNAPSHOT`).
- The Scheduler modules now live directly at the repository root (22 top-level modules and 82 Maven POMs at migration time).
- The upstream Maven baseline uses Spring Boot 2.x and an older Java toolchain. Use **JDK 11** for the imported Scheduler build/runtime checks while the upstream Lombok baseline is retained. Do not automatically align its dependencies with the root Yak Ops/CDC build.
- Core Java packages remain under `org.apache.dolphinscheduler` for upstream comparison.

`MIGRATION_MANIFEST.json` is the **historical import inventory**, with upstream file checksums and exclusions; it is not a live inventory of subsequent cleanup changes. `UPSTREAM.md` and `UPSTREAM_ROOT_POM.xml` retain the source provenance. Keep Apache `LICENSE`, `NOTICE`, file headers, and applicable third-party license files.

## Architecture

| Area | Responsibility |
| --- | --- |
| `yak-ops-master` | Workflow command processing, execution graph, lifecycle/state machine, dispatch, and failover |
| `yak-ops-worker`, `yak-ops-task-executor` | Task execution and lifecycle handling |
| `yak-ops-scheduler-plugin` | `SchedulerApi` and Quartz cron triggers |
| `yak-ops-api`, `yak-ops-service` | Workflow, schedule, and management services |
| `yak-ops-dao`, `yak-ops-dao-plugin` | Persistence, SQL schema, and database dialects |
| `yak-ops-registry`, `yak-ops-extract`, `yak-ops-eventbus` | Registration/coordination, RPC contracts, and internal events |
| `yak-ops-spi`, `yak-ops-task-plugin`, `yak-ops-datasource-plugin`, `yak-ops-storage-plugin`, `yak-ops-alert` | Extension points and plugin implementations |
| `yak-ops-standalone-server` | Single-JVM startup combining the imported services for development and smoke testing |

**Quartz does not directly run Yak Ops CDC pipelines.** The imported scheduler associates a schedule with a DolphinScheduler workflow definition. Quartz triggers a workflow command, then Master drives the workflow lifecycle and sends tasks to Worker.

The imported Standalone mode is a development/smoke-test entry point with H2; it is not a production-ready integrated Yak Ops server. H2 in-memory state does not survive a restart.

## Current integration status

Earlier JDK 11 CI checks compiled the original 99-module reactor; validate the current 82-module reactor after optional-plugin cleanup, exercised selected Master/EventBus tests and an H2-backed workflow, and started Standalone with a healthy API/Master/Worker/Alert/DB response. **These checks do not establish that all plugins work, Java 21 is compatible, or Yak Ops UI/CDC is integrated.**

The separate DolphinScheduler UI, distribution, and several optional big-data task/data-source plugins were not imported. Some residual Kubernetes/YARN/Hadoop code may still exist in common APIs; do not infer that their integrations are supported.

## Build and run

From the repository root:

```sh
# Inspect the imported Maven reactor
bash mvnw -B -ntp -pl :yak-ops validate

# With JDK 11: compile the imported Scheduler modules
bash scripts/compile-scheduler.sh

# With JDK 11: development smoke startup using H2
bash scripts/run-scheduler-standalone.sh
```

After a successful Standalone startup, the health endpoint is:
`http://127.0.0.1:12345/dolphinscheduler/actuator/health`.

See [DEVELOPMENT.md](../DEVELOPMENT.md) for the verified test commands, module responsibilities, and maintenance rules, and [AGENTS.md](../AGENTS.md) for repository constraints.

## Maintenance notes

The imported Java/test/resource trees are intentionally left untouched by the first-stage documentation cleanup. The obsolete upstream distribution plugin inventory (`config/plugins_config`) and module-level `CLAUDE.md` files were removed; maintain shared architecture notes in `DEVELOPMENT.md`. Keep the migration manifest for historical comparison, not as a current-file-existence assertion.

## Stage 4 plugin scope

The Maven reactor retains all task plugins and the SPI/APIs for datasource, storage and alerts. Optional plugin implementations removed in Stage 4:

- **Datasources:** Redshift, Snowflake, Azure SQL, SAP HANA, Vertica, DolphinDB, and Databend. Retained: MySQL, PostgreSQL, Oracle, SQL Server, ClickHouse, DB2, OceanBase, Dameng, StarRocks, Doris, and SSH.
- **Storage:** GCS, Azure Blob (ABS), Huawei OBS, Tencent COS. Retained: S3 and Alibaba OSS, including their remote task-log handlers.
- **Alerts:** PagerDuty, Slack, Webex Teams, Telegram, Aliyun Voice, and Prometheus. Retained: email, HTTP, script, DingTalk, Feishu, and WeChat.

Existing database enum values and historical SQL migrations remain for compatibility, but removing a plugin means that backend can no longer load that implementation. The unsupported Azure Data Factory Cloud API (controller, service and Azure SDK dependencies) was also removed after it prevented credential-free Standalone startup. Quartz, the task executor and Standalone still use their original integration model.
