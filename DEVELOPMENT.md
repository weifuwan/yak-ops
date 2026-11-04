# Developing Yak Ops

## Modules

- yak-ops-ui: existing frontend application
- yak-ops-cdc: standalone Flink CDC pipeline implementation
- yak-ops-scheduler: Apache DolphinScheduler 3.4.3-release source migration at commit 26228f9c5bff24b3ded7095484caf7a5ecffa050

This is an upstream-preserving source migration with scoped CI acceptance: all 99 Maven reactor modules compile on JDK 11, one H2-backed Workflow integration test and selected Master/EventBus tests pass, and Standalone's H2 health endpoint reports UP for API, Master, Worker, Alert and database. This does not certify all plugins, Java 21 compatibility or CDC integration. Java source, resource and test files in the selected modules are preserved as upstream. Only Maven module coordinates, module inclusion and dependencies on excluded optional plugin modules have been adapted. Consult yak-ops-scheduler/MIGRATION_MANIFEST.json before changing or deleting migrated code.

## Scheduler architecture notes (consolidated)

The module-level `yak-ops-scheduler/**/CLAUDE.md` files were removed because many described upstream modules and deployment paths that were not imported. Keep shared design and maintenance notes here instead.

- **Workflow execution:** `yak-ops-master` consumes persisted workflow commands and owns workflow/task state machines, execution graph, dispatch, event handlers, and failover. `yak-ops-worker` uses `yak-ops-task-executor` and task plugins to execute dispatched tasks. Do not bypass workflow lifecycle events with direct ad-hoc state changes.
- **Scheduling:** `yak-ops-scheduler-plugin` exposes `SchedulerApi` with a Quartz implementation. The current schedule is bound to a DolphinScheduler workflow definition: cron fires -> workflow command is inserted -> Master processes the workflow -> Worker executes tasks. Do not present Quartz as directly scheduling Yak Ops CDC until the separate integration is implemented. Keep Quartz types behind the scheduler abstraction.
- **Communication and coordination:** `yak-ops-registry` provides server registration and coordination; `yak-ops-extract` contains Master/Worker/Alert RPC contracts; `yak-ops-eventbus` handles in-process engine events. Changing RPC contracts, leader/failover behavior, or workflow lifecycle requires targeted tests.
- **Persistence:** `yak-ops-dao` contains entities, mappers, repositories, SQL schema/upgrade assets; `yak-ops-dao-plugin` supplies H2/MySQL/PostgreSQL dialect implementations. Preserve database entity/column mappings and evaluate schema upgrade paths together with code changes.
- **Extension points:** `yak-ops-spi`, task/data-source/storage/scheduler/alert plugins are separate concerns. Task factories are discovered through SPI and task type names may be persisted in the database; removing or renaming one is a compatibility decision. `yak-ops-bom` manages imported dependency versions; `yak-ops-common`, `yak-ops-service`, `yak-ops-meter`, and `yak-ops-authentication` provide shared infrastructure.
- **Standalone:** `yak-ops-standalone-server` starts the imported API/Master/Worker/Alert services in one JVM for development and smoke testing. Default H2 state is ephemeral. Standalone's Maven module declares many dependencies as `provided`; use the documented launcher/classpath rather than assuming a standalone jar can run alone. Stage 2 removed the independent API/Master/Worker/Alert launch scripts and their assembly distributions, but retained the service JARs required by Standalone. The Maven-based `scripts/run-scheduler-standalone.sh` development entry point remains the supported smoke-test path. Standalone's original distribution `bin/start.sh` is retained for provenance; its assembled runtime/classpath has not been separately validated.
- **Tools and provenance:** `yak-ops-tools` retains schema initialization/upgrade code, SQL and `upgrade-schema.sh`; the one-off lineage/resource migration classes and launchers were removed in stage 2. Its assembly now points at the imported `yak-ops-dao` and `yak-ops-common` resource trees. `yak-ops-scheduler/MIGRATION_MANIFEST.json` records the **original import baseline**, not subsequent changes to the checked-in tree. Keep Apache `LICENSE`, `NOTICE`, and relevant third-party license files. The obsolete original-distribution `config/plugins_config` inventory was removed in the documentation cleanup.
- **Security:** `TaskExecutionContext` and datasource parameters can contain passwords, tokens, and access keys. Avoid logging raw task contexts or credentials.

These notes describe the imported architecture, **not** a completed Yak Ops UI/CDC integration. Do not delete Master, Worker, registry, extract, Quartz, or runtime modules based only on a standalone deployment preference; first identify their callers and replace the relevant execution contracts.

## Scheduler stage 2 cleanup

- Removed the four *independent service distributions* (API, Master, Worker, Alert) and their shell launchers, not the underlying service Java classes or POM modules.
- Removed the one-time DolphinScheduler lineage/resource migration applications and launchers; kept the schema manager, SQL migrations, integration test sources and tools assembly.
- Corrected the `yak-ops-tools` assembly source paths to the `yak-ops-*` modules and made `upgrade-schema.sh` usable without the obsolete global DolphinScheduler environment script. Specify the database profile with `DATABASE=mysql` or `DATABASE=postgresql` and Spring datasource environment variables.
- Preserved `yak-ops-standalone-server`, its source launcher/configuration, and `scripts/run-scheduler-standalone.sh`. The Maven exec Standalone smoke path remains authoritative. The archived original standalone directory distribution has **not** been certified to run independently after removal of the separate service distributions.
- `MIGRATION_MANIFEST.json` continues to record historical import provenance, not a list of files guaranteed to exist after cleanup.

## Scheduler stage 3: Kubernetes / YARN runtime cleanup

- Removed unused Kubernetes and YARN application managers, Kubernetes task execution classes and Kubernetes task context fields. The shell command executor now cancels the local process tree only; this does **not** kill remote YARN/Kubernetes jobs.
- Removed Kubernetes Namespace management endpoints, permission/audit objects, mapper/repository Java code, namespace tests and the direct Fabric8 Kubernetes client dependencies.
- Removed Master’s explicit Hadoop client/common dependencies, while keeping Hadoop in the datasource API where `CommonUtils` still implements Kerberos `UserGroupInformation` logic.
- Preserved workflow, Master, Worker, Quartz, standalone startup, data source Kerberos support, and historical SQL upgrades (including old namespace tables for compatibility).
- Legacy `Queue` CRUD and `YARN_QUEUE` audit labels remain because user/tenant queue metadata and their APIs are still coupled to these models. They do not provide a YARN application client.
- The original import manifest is historical provenance; removed files will still appear in its original source list. Revalidate compilation and selected workflow tests after this change.

## Build commands

Use Java 21, Git and the checked-in Maven wrapper for the existing Yak Ops modules. On Windows, run the shell wrapper under Git Bash or WSL.

```sh
# Validate Scheduler Maven model
bash mvnw -B -ntp -pl :yak-ops-scheduler validate

# Target one imported module with its dependencies
# Use JDK 11 for the unmodified upstream Lombok/compiler combination
bash mvnw -B -ntp -pl :yak-ops-eventbus -am -DskipTests -Dspotless.skip=true package

# Check CDC formatting without scanning upstream Scheduler code
bash mvnw -B -ntp -f yak-ops-cdc/pom.xml com.diffplug.spotless:spotless-maven-plugin:3.10.2:check

# Build CDC CLI without tests
bash mvnw -B -ntp -pl :yak-ops-cdc-cli -am -DskipTests package

# Run UI formatting/type/build checks
cd yak-ops-ui
npm ci
npm run format:check
npm run typecheck
npm run build
```

Use JDK 11 for Scheduler compilation while its upstream Lombok baseline is retained; JDK 21 currently fails on older Lombok javac internals. DolphinScheduler originally targets Java 8, Maven version 3.4.4-SNAPSHOT and Spring Boot 2.x. The imported module coordinates use 1.4.0 and an independent upstream-based Maven parent. Do not silently align these dependencies with the CDC Java 17/Flink 1.20.3 baseline or the root Spring Boot 3.3.x parent.

## Scheduler Stage 6 acceptance (JDK 11)

The Maven compile and test commands below were exercised in GitHub Actions. The local launcher follows the equivalent successfully verified Standalone build/resource/startup steps, but the helper script itself was not separately executed. `mvn test` was not run across all 99 modules.

```sh
# Compile all selected Scheduler modules, skipping test source compilation
bash mvnw -B -ntp -f yak-ops-scheduler/pom.xml \
  -Dmaven.test.skip=true -Dspotless.skip=true -Djacoco.skip=true -Danalyze.skip=true compile

# Selected Master lifecycle / state-machine tests
bash mvnw -B -ntp -f yak-ops-scheduler/pom.xml \
  -pl :yak-ops-master -am \
  -Dtest=TaskRetryLifecycleEventTest,TaskForceSuccessStateActionTest,WorkflowInstanceRepeatTriggerTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dspotless.skip=true -Djacoco.skip=true -Danalyze.skip=true test

# Execute one real H2-backed Workflow with an upstream Fake Task
bash mvnw -B -ntp -f yak-ops-scheduler/pom.xml \
  -pl :yak-ops-master -am \
  -Dtest=WorkflowStartBasicTestCase#testStartWorkflow_with_oneSuccessTask \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dspotless.skip=true -Djacoco.skip=true -Danalyze.skip=true test

# Build and launch Standalone with original H2 configuration
bash scripts/run-scheduler-standalone.sh
```

After Standalone starts, its health endpoint is `http://127.0.0.1:12345/dolphinscheduler/actuator/health`. In CI it reported `UP` with `api`, `master`, `worker`, `alert` and `db` components UP. The helper supplies upstream API YAML and DAO H2 SQL to the Maven runtime classpath, without touching Java source.

The upstream Aliyun OSS dependency indirectly introduces `stax-api:1.0.1`, which shadows JDK XML streaming and breaks Jackson XML. `yak-ops-common/pom.xml` explicitly excludes this old transitive dependency; the OSS SDK itself is retained. The default standalone JAR packaging and distribution are **not** certified by the Maven exec smoke test.

CI evidence: [full compile and EventBus tests](https://github.com/weifuwan/yak-ops/actions/runs/41788436423), [Workflow integration](https://github.com/weifuwan/yak-ops/actions/runs/41786829369), and [Standalone health smoke](https://github.com/weifuwan/yak-ops/actions/runs/41788492871).

## Migration and testing policy

- Before any change, compare the source file to its upstream SHA-256 in the manifest.
- Keep org.apache.dolphinscheduler Java packages and Apache-2.0 headers until an explicit refactoring decision.
- Keep tests and resources for all imported modules. An unexecuted test is not a pass.
- Native Scheduler Maven validation, dependency resolution, compilation and Standalone runtime acceptance are separate milestones.
- Optional YARN, Spark, Hadoop, Hive, Kubernetes and original DolphinScheduler UI features are not declared operational.
- The Yak Ops UI and CDC modules are not yet wired to the imported Scheduler API and Task Engine.
- Use GitHub Actions Code Format for the original UI/CDC code and Scheduler Build for imported Maven verification.

The original DolphinScheduler root POM remains under yak-ops-scheduler/UPSTREAM_ROOT_POM.xml for provenance. Re-running the importer overwrites nothing: it fails if the destination exists.
