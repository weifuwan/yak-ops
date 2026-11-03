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

Run relevant tests before the full reactor. Ordinary Maven `verify` runs unit and architecture tests without Docker. Database tests use the explicit `integration-tests` profile and Failsafe. An unexecuted test is not a pass.

Frontend commands and quality checks are documented in [frontend tooling](yak-ops-ui/docs/tooling.md).

## Test types and execution

| Tests | Ownership | Execution |
| --- | --- | --- |
| Unit / frontend contracts | Java modules / `yak-ops-ui` | Affected PR quality checks |
| Business database IT | Business modules | Selected backend PRs; full release suite |
| UI automation / deployment | Playwright / Docker Compose | Release; manual as needed |

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

The [Nightly Backend Acceptance workflow](.github/workflows/nightly-backend-acceptance.yml) runs `suite: full` every day at 18:00 UTC (02:00 Asia/Singapore) on the default branch. It is also manually runnable. Nightly is additional coverage, not a replacement for affected PR checks or the full Release Gate; failure, skip or missing evidence must not be treated as success.

The [Backend Tests workflow](.github/workflows/backend-tests.yml) selects the registered Business and CDC suites from the complete PR diff. MySQL CDC Source unit tests follow Flink CDC test names; upstream `*ITCase` classes remain outside the current Failsafe `*IT` registry. The connector-local `cdc` suite runs `TableExclusionDuringSnapshotIT` with a real database, and CDC production changes select the CDC suite. Cross-process crash-recovery acceptance is not covered by the remaining database integration suites. Shared or unrecognized backend changes still select the full registered matrix, and missing, skipped or failed selected reports block the check.

Reports live in each module's `target/failsafe-reports/` and `target/test-artifacts/`; the root `target/test-artifacts/result.json` records the tested SHA, command, selected suites and observed method counts. Diagnostics are captured before cleanup, including container logs and database test diagnostics on failure. No credentials or full sensitive task payloads belong in reports. A missing Docker daemon, skipped selected test or empty report fails the run.

### Playwright UI Tests

Playwright is **not a default feature PR gate**. The standalone [Playwright UI Tests workflow](.github/workflows/playwright-ui-tests.yml) runs on manual dispatch and when its own tests/workflow change. Its mocked scenarios run against the **built** frontend, not the Vite development server. Ordinary UI feature PRs rely on frontend quality and contract checks; reviewers may explicitly request a browser run for high-risk interactions.

The formal [Release Gate](.github/workflows/v1-release-gate.yml) always runs the **full** UI suite against a disposable Docker Compose stack containing the exact candidate images. The suite includes mocked interaction contracts and a real authenticated login/navigation smoke. UI tests must not wait for real JDBC/CDC, checkpoints, or Cron triggering; these belong to backend integration tests.

```sh
python3 -m pip install playwright==1.57.0
python3 -m playwright install chromium
# Serve yak-ops-ui's production build on http://127.0.0.1:5173
python3 yak-ops-ui/tests/e2e/run.py --suite mocked --base-url http://127.0.0.1:5173
```

The Playwright report is `/tmp/yak-playwright/v<VERSION>/<SHA>/playwright-results.json`, with per-scenario Playwright traces and failure screenshots. Only results from the **exact release version and SHA** apply to that build. A skipped, failed, cancelled, or missing required run blocks release.

### Test and review evidence

Connector integration workflows collect their own test reports. Playwright has its own versioned artifact. Preserve failures and traceback while redacting credentials and task parameters. Use condition-based bounded waits; arbitrary sleep, hiding failed assertions through retries, or changes to production checkpoint timing are prohibited.


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

Keep JavaDoc and implementation comments in English. Checkstyle validates the configured backend source packages; public interfaces and non-obvious lifecycle contracts require useful documentation. Logging validation enforces SLF4J structure, sensitive-data safety and English internal log templates. Core and Runtime diagnostic strings and Connector technical exception messages use English; user-facing ErrorCode and validation messages remain localized. TRACE remains supported. In CI, Checkstyle and Spotless run before `verify`; their repeated Maven executions are skipped only after both checks pass. Plain local `verify` still enforces them.

## Documentation and Design

Follow the [current-state comment and documentation rules](AGENTS.md#current-state-comments-and-documentation) when changing JavaDoc, implementation comments or active project guidance.

- Major design changes and public contract changes go through the [YOIP process](.github/CONTRIBUTING.md#yak-ops-improvement-proposals-yoips) before implementation.
- Ordinary implementation details belong in code, JavaDoc, tests and the GitHub PR; no companion descriptive Markdown is required.
- Change user documentation for new or changed user-visible functionality, compatibility or operation procedures.
- Consult current code, JavaDoc, tests and accepted YOIPs for behavior and design decisions.
- Versioned user-facing release notes live in [docs/releases](docs/releases/README.md), reviewed before the Release Gate. Release decisions and test evidence remain in Gate artifacts; published notes and immutable asset evidence also appear in GitHub Releases.
