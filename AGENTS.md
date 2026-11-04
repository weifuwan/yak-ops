# Yak Ops Agent Instructions

## Scope

The repository contains only two product modules:

- `yak-ops-cdc`: Flink CDC-inspired pipeline CLI, composer, runtime, MySQL source and JDBC sink; preserve its internal Maven modules, upstream source attribution and Flink 1.20.3 alignment.
- `yak-ops-ui`: existing frontend workspace. Do not assume that its previous backend API exists.

The root `pom.xml` is a Maven reactor/parent, not a separate backend application. Do not reintroduce the removed Business, Boot, DAO, Platform, Plugin, Worker, Distribution or Registry modules without an explicit product decision.

## Commands

- Java 21, Git and the Maven wrapper. For Windows use Git Bash/WSL to run `mvnw`.
- CDC CLI build: `bash mvnw -B -ntp -pl :yak-ops-cdc-cli -am -DskipTests package`.
- CDC formatting: `bash mvnw -B -ntp -pl '!yak-ops-ui' com.diffplug.spotless:spotless-maven-plugin:3.10.2:check`.
- UI setup and checks: `cd yak-ops-ui && npm ci && npm run format:check && npm run typecheck && npm run build`.
- See [DEVELOPMENT.md](DEVELOPMENT.md) and [UI tooling](yak-ops-ui/docs/tooling.md).

## Engineering Rules

- Read current source, callers, parent POMs and affected Maven reactor dependencies before editing.
- Prefer upstream Flink/Flink CDC models when semantics match. Do not accidentally mix incompatible Flink versions or replace upstream source with unsupported custom behavior.
- Keep Source, Transform, Sink, schema events, data events, checkpoint and JDBC behavior explicit. Preserve error causes and cleanup; do not claim exactly-once guarantees without evidence.
- Do not expose credentials, sensitive row values or serialized state in diagnostic logs.
- Prefer small, complete changes; distinguish verified facts, assumptions and recommendations. Clearly state commands run, results and unexecuted checks.
- CDC and UI are currently independent. Do not claim that the frontend is served by, or controls, the CDC CLI.
