# Developing Yak Ops

This document covers the Yak Ops development environment and verification commands. For the contribution process and YOIPs, see [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## Preparation

- Git and Java 21.
- Bash (WSL or Git Bash on Windows) to run the checked-in `mvnw` shell wrapper.
- Node.js and npm for `yak-ops-ui`; use the frontend's own package and tooling files for supported versions and commands.
- Docker and database credentials only when running relevant integration or release checks.

Clone the repository and open its root `pom.xml` as a Maven project in IntelliJ IDEA. Use the command line to distinguish Maven failures from IDE configuration problems. Build with the repository's Java 21 and checked-in Maven configuration.

## Build and Test

Full local backend verify (includes CDC modules, Spotless and ordinary tests; not the automatic CI scope):

```sh
bash mvnw -pl '!yak-ops-ui,!yak-ops-dist' verify
```

Apply/check the existing Java formatter:

```sh
bash mvnw -DskipTests -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' com.diffplug.spotless:spotless-maven-plugin:3.10.2:apply
bash mvnw -DskipTests -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' com.diffplug.spotless:spotless-maven-plugin:3.10.2:check
```

Run relevant tests before the full reactor. Ordinary Maven `verify` runs unit and architecture tests without Docker. Database tests use the explicit `integration-tests` profile and Failsafe. An unexecuted test is not a pass.

Frontend commands and quality checks are documented in [frontend tooling](yak-ops-ui/docs/tooling.md).

## Test types and execution

| Tests | Ownership | Execution |
| --- | --- | --- |
| Backend unit tests | Native Yak Ops Java modules (CDC excluded) | PR and main/dev CI |
| Business database IT | Business modules | Selected backend PRs and main; manually runnable |
| Frontend / backend formatting | Oxfmt / Spotless | PR and main/dev CI |

### Backend integration tests

Use Java 21 and a working Docker daemon. Tests create their own disposable databases on dynamic ports; no external database URL or shared environment is needed. `yak-ops-test-utils` is a test-only dependency and must not enter application distributions.

```sh
bash scripts/tests/run.sh business
bash scripts/tests/run.sh cdc
bash scripts/tests/run.sh full
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

### Test quality

Cover the smallest stable behavior contract that can catch a real regression. Choose a deterministic unit test for pure logic or serialized state; choose a real database integration test for CDC or task persistence semantics; use the appropriate integration suite for durable CDC and cross-component guarantees. Do not mirror each production class with its own test by default or reproduce the same fixtures across every database. Shared behavior belongs in reusable test bases; vendor-specific behavior stays in the owning tests.

Before adding tests, identify the distinct failure they would detect and check existing unit and IT scenarios. Prefer outcome assertions over private-field reflection and Mockito invocation checks, except where the interaction is an observable requirement. For failed CDC, checkpoint or XA scenarios, validate the production behavior rather than weakening assertions or simply deleting tests. For test removal, keep an explicit replacement/coverage mapping in the PR.

Each command is one Maven invocation. Failsafe discovers `*IT` classes by JUnit tags; ordinary tests use `*Test`. Register every concrete IT in `scripts/tests/backend_tests.py`. Selected classes must produce complete XML reports with no failed, skipped or missing cases. IDE execution of one IT class remains available.

Common Source/Sink behavior belongs in reusable Connector test bases, database-specific types and DDL in their subclasses. Connector and Runtime tests do not depend on HTTP or Boot. Cross-database JDBC synchronization tests were removed with the retired engine and must not be reported as covered. Transaction and concurrency checks belong in business database tests.

JUnit owns shared containers within one module's test execution. Data is isolated per scenario; integration cases isolate state and resources. Destructive and recovery cases own independent environments. Stop running tasks before deleting their data. Do not mix class-scoped container shutdown with root-store reuse, share resources across CI jobs, or enable parallel methods without proving isolation.

The [Backend Tests workflow](.github/workflows/backend-tests.yml) runs JUnit unit tests for affected native Maven modules and automatically selected Business database integration tests only. It does not include `yak-ops-cdc` (or any of its children) in the automatic unit-test reactor; main pushes run the full **native** unit-test scope and the Business database suite. Pull requests and dev pushes select affected native modules and the Business suite as appropriate. Maven or test-infrastructure changes expand native scope without enabling CDC tests.

CDC Runtime, Composer, CLI and MySQL Source tests are preserved but **manual-only**. To run them use `bash scripts/tests/run.sh cdc`, or manually dispatch [Backend Tests](.github/workflows/backend-tests.yml) with `suite=cdc` (or explicitly choose `suite=full` for both Business and CDC). The manual workflow defaults to `business`, not `full`. CDC's upstream Surefire configuration can run expensive tests and is never part of the automatic backend CI. The independent Code Format workflow still checks CDC formatting when relevant.

Reports live in each module's `target/failsafe-reports/` and `target/test-artifacts/`; the root `target/test-artifacts/result.json` records the tested SHA, command, selected suites and observed method counts. Diagnostics are captured before cleanup, including container logs and database test diagnostics on failure. No credentials or full sensitive task payloads belong in reports. A missing Docker daemon, skipped selected test or empty report fails the run.

### CI formatting and frontend verification

[Code Format](.github/workflows/format-check.yml) runs Oxfmt for frontend changes and Spotless for changed Java modules. It does not run frontend tests, lint, typecheck, build, distribution checks or release validation. Frontend typing and builds remain available locally via `yak-ops-ui/package.json`.

### Test and review evidence

Explicitly selected database integration suites collect their own test reports. Preserve failures and traceback while redacting credentials and task parameters. Use condition-based bounded waits; arbitrary sleep, hiding failed assertions through retries, or changes to production checkpoint timing are prohibited.


## Incremental Review Evidence

Follow [AGENTS.md](AGENTS.md#verification-scope-and-incremental-review) for review scope, polling and evidence reuse. Record the reviewed head, base and tested merge/tree, selected suite, command or run URL, and actual result in the PR. Follow-up fixes are reviewed against the previously reviewed SHA with affected callers and tests, not by repeatedly rereading the whole repository. Changed bases or shared contracts reopen the affected review scope. A new commit needs matching test evidence; known failures cannot disappear merely because a focused run omits their suite.

## Coding Standards

Use the [Apache Flink Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/) for code review principles: small, focused changes; meaningful names; explicit ownership; readable tests; no redundant comments.

Java formatting uses **Spotless + Palantir Java Format**. Maven Checkstyle validates JavaDoc and ArchUnit tests validate module dependency boundaries during `verify`.

```sh
bash mvnw -B -ntp -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0:check
python3 scripts/ci/test_logging_rules.py
python3 scripts/verify_logging_rules.py
```

Keep JavaDoc and implementation comments in English. Checkstyle validates the configured backend source packages; public interfaces and non-obvious lifecycle contracts require useful documentation. Logging validation enforces SLF4J structure, sensitive-data safety and English internal log templates. Core and Runtime diagnostic strings and Connector technical exception messages use English; user-facing ErrorCode and validation messages remain localized. TRACE remains supported. GitHub CI uses Spotless as the Java formatting gate. Backend test CI skips Spotless/Checkstyle within Maven because formatting is a separate workflow. Other Maven checks remain available locally; plain `verify` still runs configured lifecycle checks.

## Documentation and Design

Follow the [current-state comment and documentation rules](AGENTS.md#current-state-comments-and-documentation) when changing JavaDoc, implementation comments or active project guidance.

- Major design changes and public contract changes go through the [YOIP process](.github/CONTRIBUTING.md#yak-ops-improvement-proposals-yoips) before implementation.
- Ordinary implementation details belong in code, JavaDoc, tests and the GitHub PR; no companion descriptive Markdown is required.
- Change user documentation for new or changed user-visible functionality, compatibility or operation procedures.
- Consult current code, JavaDoc, tests and accepted YOIPs for behavior and design decisions.
- Versioned user-facing release notes live in [docs/releases](docs/releases/README.md). Release preparation and publishing are manual operations; the two CI workflows do not certify release readiness.
