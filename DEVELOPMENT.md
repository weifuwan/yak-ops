# Developing Yak Ops

## Modules

- yak-ops-ui: existing frontend application
- yak-ops-cdc: standalone Flink CDC pipeline implementation
- yak-ops-scheduler: Apache DolphinScheduler 3.4.3-release source migration at commit 26228f9c5bff24b3ded7095484caf7a5ecffa050

This is an upstream-preserving source migration with scoped CI acceptance: all 99 Maven reactor modules compile on JDK 11, one H2-backed Workflow integration test and selected Master/EventBus tests pass, and Standalone's H2 health endpoint reports UP for API, Master, Worker, Alert and database. This does not certify all plugins, Java 21 compatibility or CDC integration. Java source, resource and test files in the selected modules are preserved as upstream. Only Maven module coordinates, module inclusion and dependencies on excluded optional plugin modules have been adapted. Consult yak-ops-scheduler/MIGRATION_MANIFEST.json before changing or deleting migrated code.

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
