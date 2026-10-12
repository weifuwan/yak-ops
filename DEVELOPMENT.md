# Developing Yak Ops

This document covers the Yak Ops development environment and verification commands. For the contribution process and YOIPs, see [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## Preparation

- Git and Java 21.
- Bash (WSL or Git Bash on Windows) to run the checked-in `mvnw` shell wrapper.
- Node.js and npm for `yak-ops-ui`; use the frontend's own package and tooling files for supported versions and commands.
- Docker and database credentials only when running relevant integration or release checks.

Clone the repository and open its root `pom.xml` as a Maven project in IntelliJ IDEA. Use the command line to distinguish Maven failures from IDE configuration problems. Build with the repository's Java 21 and checked-in Maven configuration.

## Build and Test

Backend verify, including the existing Spotless check and ordinary tests:

```sh
bash mvnw -pl '!yak-ops-ui,!yak-ops-dist' verify
```

Apply/check the existing Java formatter:

```sh
bash mvnw -DskipTests -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' com.diffplug.spotless:spotless-maven-plugin:3.10.2:apply
bash mvnw -DskipTests -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' com.diffplug.spotless:spotless-maven-plugin:3.10.2:check
```

Run relevant tests before the full reactor where possible. Connector real-database ITs and product acceptance are separate from Maven `verify`. Current CI scopes and required checks are defined by [Quality Check](.github/workflows/quality-check.yml). Skipped acceptance is not successful acceptance.

Frontend commands and quality gates are documented in [frontend tooling](yak-ops-ui/docs/tooling.md). Do not run frontend or release builds to validate a Markdown-only change unless its scope requires them.

## Coding Standards

Use the [Apache Flink Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/) for code review principles: small, focused changes; meaningful names; explicit ownership; readable tests; no redundant comments.

Java formatting uses **Spotless + Palantir Java Format**. Maven Checkstyle validates JavaDoc and ArchUnit tests validate module dependency boundaries during `verify`.

```sh
bash mvnw -B -ntp -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0:check
python3 scripts/ci/test_logging_rules.py
python3 scripts/verify_logging_rules.py
```

Keep JavaDoc and implementation comments in English. Checkstyle validates the configured backend source packages; public interfaces and non-obvious lifecycle contracts require useful documentation. Logging validation enforces SLF4J structure, sensitive-data safety and English internal log templates. Core and Runtime diagnostic strings and Connector technical exception messages use English; user-facing ErrorCode and validation messages remain localized. TRACE remains supported. In CI, Checkstyle and Spotless run before `verify`; their repeated Maven executions are skipped only after both checks pass. Plain local `verify` still enforces them.

## Documentation and Design

Follow the [current-state comment and documentation rules](AGENTS.md#current-state-comments-and-documentation) when changing JavaDoc, implementation comments or active project guidance.

- Major design changes and public contract changes go through the [YOIP process](.github/CONTRIBUTING.md#yak-ops-improvement-proposals-yoips) before implementation.
- Ordinary implementation details belong in code, JavaDoc, tests and the GitHub PR; no companion descriptive Markdown is required.
- Change user documentation for new or changed user-visible functionality, compatibility or operation procedures.
- Consult current code, JavaDoc, tests and accepted YOIPs for behavior and design decisions.
- Release decisions are recorded as Release Gate artifacts; version notes and immutable asset evidence belong in GitHub Releases, not source-tree Markdown files.
