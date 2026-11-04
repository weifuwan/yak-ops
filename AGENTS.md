# Yak Ops AI Agent Instructions

## Repository boundaries

Yak Ops has three top-level product workspaces:

- yak-ops-ui: existing frontend, currently not connected to the imported Scheduler backend.
- yak-ops-cdc: Flink CDC CLI/runtime/connectors, kept independent and aligned with Flink 1.20.3.
- yak-ops-scheduler: imported Apache DolphinScheduler 3.4.3-release source (pinned commit 26228f9c5bff24b3ded7095484caf7a5ecffa050).

The root pom.xml is an aggregator. Do not merge Scheduler's Spring Boot 2.x dependency baseline into the CDC or UI subsystem merely to eliminate Maven modules.

## Preserve upstream source fidelity

- Start with yak-ops-scheduler/MIGRATION_MANIFEST.json and UPSTREAM_ROOT_POM.xml before editing upstream-derived code.
- Preserve org.apache.dolphinscheduler Java packages and upstream Apache source/license headers.
- Distinguish source import from successful compilation, runtime integration and feature support.
- Avoid changing Master/Worker execution graphs, state machines, lifecycle handling, checkpoint/recovery or scheduler semantics while doing Maven/build adaptation.
- Keep all imported module Java tests/resources, even when tests are not selected in automatic CI.
- Excluded optional modules and Maven dependencies are listed in the manifest; do not silently restore big-data plugins through all-artifact dependencies.
- Do not add an alternative Scheduler execution model or shortcut Master/Worker communication without an explicit decision.

## Build and verification

- Java 21 and checked-in Maven wrapper for Yak Ops; Scheduler source upstream targets Java 8/Spring Boot 2.x and needs separate verification.
- Scheduler model: bash mvnw -B -ntp -pl :yak-ops-scheduler validate
- Scheduler smoke (JDK 11 to avoid upstream Lombok / JDK 21 incompatibility): bash mvnw -B -ntp -pl :yak-ops-eventbus -am -DskipTests package
- CDC build: bash mvnw -B -ntp -pl :yak-ops-cdc-cli -am -DskipTests package
- UI checks: cd yak-ops-ui && npm ci && npm run format:check && npm run typecheck && npm run build
- Record exact commands and results; tests not run must not be reported as successful.

Never log credentials or sensitive CDC row payloads. Read current code, callers and dependencies before modifying them. Keep changes minimal, reviewable and upstream-comparable.
