# Phase 1 Audit: Rename DolphinScheduler Java Packages

## Decision and snapshot

- Repository: `weifuwan/yak-ops`
- Branch: `main`
- Audited branch HEAD: `81a6a734d5cacf3c9f923148d206f73d0d6c00d1` (2026-11-04)
- Approved transformation: `org.apache.dolphinscheduler` → `io.yak.ops`
- Preserve every subpackage suffix, class name, module name and Maven artifact ID.
- **Do not** rename `org.apache.flink.cdc` in `yak-ops-cdc` as part of this work.
- This phase is **inventory only**: no Java source, package declarations, build configuration, Spring configuration or runtime behavior is changed.

## Exact tracked-path inventory

Based on a complete, non-truncated GitHub Git tree for the above `main` snapshot.

| Metric | Count |
| --- | ---: |
| Tracked files | 3369 |
| Java files across all modules | 2624 |
| Java files under `org/apache/dolphinscheduler/` | **1952** |
| Main-source Java files to move | 1558 |
| Test-source Java files to move | 394 |
| Affected first-level Maven module directories | 21 |
| Existing Java files under `io/yak/ops/` | 0 |
| Direct old-path → new-path collisions | 0 |
| Duplicate prospective fully qualified Java names from the tracked old paths | 0 |
| Non-Java resources under `org/apache/dolphinscheduler/` | **40** |

The 40 resource files are MyBatis mapper XML files in
`yak-ops-dao/src/main/resources/org/apache/dolphinscheduler/dao/mapper/`.
Their resource paths, XML `namespace`, `resultType`, `parameterType`, `type` and related values must move together with the Java mapper/entity types.

### Java files by first-level module

| Module | Main Java | Test Java | Total |
| --- | ---: | ---: | ---: |
| `yak-ops-api` | 280 | 115 | 395 |
| `yak-ops-master` | 278 | 65 | 343 |
| `yak-ops-dao` | 180 | 50 | 230 |
| `yak-ops-task-plugin` | 173 | 39 | 212 |
| `yak-ops-datasource-plugin` | 92 | 33 | 125 |
| `yak-ops-common` | 96 | 22 | 118 |
| `yak-ops-extract` | 106 | 3 | 109 |
| `yak-ops-alert` | 71 | 22 | 93 |
| `yak-ops-registry` | 63 | 10 | 73 |
| `yak-ops-task-executor` | 58 | 0 | 58 |
| `yak-ops-worker` | 30 | 5 | 35 |
| `yak-ops-spi` | 31 | 2 | 33 |
| `yak-ops-service` | 19 | 4 | 23 |
| `yak-ops-tools` | 7 | 16 | 23 |
| `yak-ops-scheduler-plugin` | 18 | 4 | 22 |
| `yak-ops-storage-plugin` | 18 | 2 | 20 |
| `yak-ops-dao-plugin` | 17 | 0 | 17 |
| `yak-ops-authentication` | 9 | 2 | 11 |
| `yak-ops-meter` | 7 | 0 | 7 |
| `yak-ops-eventbus` | 4 | 0 | 4 |
| `yak-ops-standalone-server` | 1 | 0 | 1 |
| **Total** | **1558** | **394** | **1952** |

## Configuration, reflection and runtime-sensitive references

The following are **confirmed** old-name references in files read from `main`, not guesses inferred from Java paths.

| Area | Examples / required action |
| --- | --- |
| Spring component scanning | `yak-ops-common/.../CommonConfiguration.java`, `yak-ops-service/.../ServiceConfiguration.java` and `yak-ops-dao/.../DaoConfiguration.java` have literal `@ComponentScan` package strings. Update them with package declarations. |
| MyBatis Java mapper scanning | `yak-ops-dao/.../DaoConfiguration.java` and the registry JDBC `JdbcRegistryAutoConfiguration.java` contain literal `@MapperScan` packages. Update these as well as the XML files. |
| MyBatis resources | `40` mapper XML resources have old directory names; `application.yaml` files in API, Master, Alert, Tools, Standalone and test resources use old `mapper-locations` and `type-aliases-package` values. |
| Spring Boot auto-config | 9 `META-INF/spring.factories` descriptors contain old fully qualified auto-configuration class names. |
| Class-name based logging | `logback-spring.xml` / test `logback.xml` include old class names for `SensitiveDataConverter`, `TaskLogFilter`, and `TaskLogDiscriminator`. |
| Quartz runtime | `yak-ops-api/src/main/resources/application.yaml` sets `org.quartz.threadPool.class` to an old class name. **Existing database rows** in `QRTZ_JOB_DETAILS.job_class_name` may also need a data migration or an explicitly supported reset strategy. |
| Process launchers | `scripts/run-scheduler-standalone.sh`, `yak-ops-standalone-server/src/main/bin/start.sh`, and `yak-ops-tools/src/main/bin/upgrade-schema.sh` invoke fully qualified old Java main classes. |
| Protobuf / generated fixtures | `yak-ops-task-plugin/yak-ops-task-grpc/src/test/proto/{taskTester,parserTester}.proto` set old `package` / `java_package` values, also present in paired JSON test fixtures. Update them together, or explicitly keep the wire package for compatibility. |
| Maven and formatter | Root `pom.xml` has a Spotless exclude path containing `org/apache/dolphinscheduler` and an old Maven dependency coordinate in `ignoredDependency`. The former is a resource path; review the latter as a Maven coordinate, **not** a Java package. `style/eclipse.importorder` also refers to the old Java prefix. |
| Legacy documentation | `docs/scheduler/UPSTREAM_ROOT_POM.xml` and `docs/scheduler/MIGRATION_MANIFEST.json` are upstream provenance artifacts; do not blindly rewrite them. Apache LICENSE, NOTICE and copyright headers must remain intact. |

At this snapshot, `META-INF/services/` has 6 tracked service-provider descriptor files, all in CDC; there is no corresponding old DolphinScheduler service descriptor filename in that directory list. Leave the CDC descriptors untouched. The search also found `Class.forName(jdbcDriverClassName)` in the datasource API; it dynamically loads configured JDBC drivers, so this call is **not** itself evidence of a DolphinScheduler package-name dependency.

**Concurrent main branch note:** `main` advanced from `6e2a26e` to `81a6a73` during this audit. The intervening commit removed old database-upgrade SQL files, including two historical Quartz class-name statements. Those deleted scripts are **not** part of the current resource inventory; the risk of previously persisted Quartz job names remains until runtime/data compatibility is established.

## Risks and follow-up gates

1. **Source consistency**: rename package declarations, imports, Java source directories and tests together, preserving existing package suffixes.
2. **Resource consistency**: update mapper XML paths and namespaces together with YAML mapper-locations, Spring auto-configuration, component/mapper scan strings, logging, shell entry points, formatter import order, and proto fixtures.
3. **Persisted compatibility**: inspect stored Quartz job-class names and any other persisted polymorphic class strings before deciding between data migration, compatibility alias or reset. Avoid rewriting historical provenance snapshots.
4. **Collision gate**: check the new package names and generated sources again immediately before source edits; this audit only proves no collisions among **tracked** files at the audited HEAD.
5. **Verification gate**: use JDK 11 for the imported Scheduler tests, run the existing targeted backend test command and a suitable Standalone smoke test. Keep CDC package names and tests separate. After the rename, search for remaining old **runtime** references rather than requiring historical documentation to contain none.
6. **License gate**: keep original Apache-2.0 source headers and project notices. A changed Java package does not remove upstream attribution obligations.

## Next execution boundary

**Phase 2** is Java source/test package relocation and import rewriting; **Phase 3** is configuration, mapper XML and runtime descriptor migration; **Phase 4** is targeted compilation/tests and fixes; **Phase 5** is residual reference cleanup. None of those phases is performed by this audit.
