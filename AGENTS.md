# Yak Ops AI Agent Instructions

## Repository boundaries

Yak Ops has one root Maven reactor containing Scheduler's first-level modules plus the CDC and UI workspaces:

- yak-ops-ui: existing frontend, currently not connected to the imported Scheduler backend.
- yak-ops-cdc: Flink CDC CLI/runtime/connectors, kept independent and aligned with Flink 1.20.3.
- root Scheduler modules: imported Apache DolphinScheduler 3.4.3-release source (pinned commit 26228f9c5bff24b3ded7095484caf7a5ecffa050).

The root pom.xml is the unified Scheduler-derived parent. CDC keeps Java 17 and Spring Boot 3.x dependency management in yak-ops-cdc/pom.xml; preserve its isolated build and format rules.

## Preserve upstream source fidelity

- Start with docs/scheduler/MIGRATION_MANIFEST.json and docs/scheduler/UPSTREAM_ROOT_POM.xml before editing upstream-derived code.
- Preserve org.apache.dolphinscheduler Java packages and upstream Apache source/license headers.
- Distinguish source import from successful compilation, runtime integration and feature support.
- Avoid changing Master/Worker execution graphs, state machines, lifecycle handling, checkpoint/recovery or scheduler semantics while doing Maven/build adaptation.
- Keep all imported module Java tests/resources, even when tests are not selected in automatic CI.
- Excluded optional modules and Maven dependencies are listed in the manifest; do not silently restore big-data plugins through all-artifact dependencies.
- Do not add an alternative Scheduler execution model or shortcut Master/Worker communication without an explicit decision.

## Build and verification

- Java 21 and checked-in Maven wrapper for Yak Ops; Scheduler source upstream targets Java 8/Spring Boot 2.x and needs separate verification.
- Scheduler JDK 11 status: full 99-module compile, targeted Master + EventBus tests, H2-backed Workflow integration and Standalone health smoke passed in CI. Java 21 compatibility and all-plugin coverage remain unverified.
- Scheduler startup (JDK 11): bash scripts/run-scheduler-standalone.sh
- Scheduler model: bash mvnw -B -ntp -pl :yak-ops validate
- Scheduler smoke (JDK 11 to avoid upstream Lombok / JDK 21 incompatibility): bash mvnw -B -ntp -pl :yak-ops-eventbus -am -DskipTests package
- CDC formatting check (manual): bash mvnw -B -ntp -f yak-ops-cdc/pom.xml com.diffplug.spotless:spotless-maven-plugin:3.10.2:check
- CDC build: bash mvnw -B -ntp -pl :yak-ops-cdc-cli -am -DskipTests package
- UI checks (manual): cd yak-ops-ui && npm ci && npm run format:check && npm run typecheck && npm run build
- Record exact commands and results; tests not run must not be reported as successful.

## Local formatting

- Scheduler Maven modules: `bash mvnw -B -ntp spotless:apply` (verify with `bash mvnw -B -ntp spotless:check`).
- CDC Java: `bash mvnw -B -ntp -f yak-ops-cdc/pom.xml com.diffplug.spotless:spotless-maven-plugin:3.10.2:apply` (verify with the corresponding `:check` goal).
- UI: run `npm run format` in `yak-ops-ui` (verify with `npm run format:check`).
- Formatting is a local developer responsibility, not a GitHub Actions gate. Apply the formatter before committing relevant changes.

## CI policy

- Maintain exactly one long-lived GitHub Actions workflow: `.github/workflows/backend-tests.yml`. Do not add a new workflow, formatting gate, or automated migration without explicit approval.
- Automatic CI runs only a bounded set of real Scheduler unit tests using JDK 11. UI, CDC and Scheduler formatting is handled locally, not in CI. Backend test reports must prove that tests executed.
- CDC unit/integration tests, frontend unit/E2E tests, complete Scheduler/CDC compilation, Windows builds, database integration, Standalone smoke tests, dependency audits and source-import jobs are manual-only. Run the relevant local commands when needed; do not leave one-off workflows in the repository.
- Keep Scheduler's JDK 11 verification separate from CDC's toolchain and dependency baseline. A successful scoped CI run does not certify a full reactor or integration test.

Never log credentials or sensitive CDC row payloads. Read current code, callers and dependencies before modifying them. Keep changes minimal, reviewable and upstream-comparable.
