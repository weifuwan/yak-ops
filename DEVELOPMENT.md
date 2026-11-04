# Developing Yak Ops

## Modules

- yak-ops-ui: existing frontend application
- yak-ops-cdc: standalone Flink CDC pipeline implementation
- yak-ops-scheduler: Apache DolphinScheduler 3.4.3-release source migration at commit 26228f9c5bff24b3ded7095484caf7a5ecffa050

This is a source migration, not a claim that the Scheduler is operational. Java source, resource and test files in the selected modules are preserved as upstream. Only Maven module coordinates, module inclusion and dependencies on excluded optional plugin modules have been adapted. Consult yak-ops-scheduler/MIGRATION_MANIFEST.json before changing or deleting migrated code.

## Build commands

Use Java 21, Git and the checked-in Maven wrapper for the existing Yak Ops modules. On Windows, run the shell wrapper under Git Bash or WSL.

```sh
# Validate Scheduler Maven model
bash mvnw -B -ntp -pl :yak-ops-scheduler validate

# Target one imported module with its dependencies
bash mvnw -B -ntp -pl :yak-ops-eventbus -am -DskipTests package

# Build CDC CLI without tests
bash mvnw -B -ntp -pl :yak-ops-cdc-cli -am -DskipTests package

# Run UI formatting/type/build checks
cd yak-ops-ui
npm ci
npm run format:check
npm run typecheck
npm run build
```

DolphinScheduler originally targets Java 8, Maven version 3.4.4-SNAPSHOT and Spring Boot 2.x. The imported module coordinates use 1.4.0 and an independent upstream-based Maven parent. Do not silently align these dependencies with the CDC Java 17/Flink 1.20.3 baseline or the root Spring Boot 3.3.x parent.

## Migration and testing policy

- Before any change, compare the source file to its upstream SHA-256 in the manifest.
- Keep org.apache.dolphinscheduler Java packages and Apache-2.0 headers until an explicit refactoring decision.
- Keep tests and resources for all imported modules. An unexecuted test is not a pass.
- Native Scheduler Maven validation, dependency resolution, compilation and Standalone runtime acceptance are separate milestones.
- Optional YARN, Spark, Hadoop, Hive, Kubernetes and original DolphinScheduler UI features are not declared operational.
- The Yak Ops UI and CDC modules are not yet wired to the imported Scheduler API and Task Engine.
- Use GitHub Actions Code Format for the original UI/CDC code and Scheduler Build for imported Maven verification.

The original DolphinScheduler root POM remains under yak-ops-scheduler/UPSTREAM_ROOT_POM.xml for provenance. Re-running the importer overwrites nothing: it fails if the destination exists.
