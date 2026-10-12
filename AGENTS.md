# Yak Ops AI Agent Instructions

Guidance for contributors and AI coding agents working in this repository. Follow the [Apache Flink contribution process](https://flink.apache.org/how-to-contribute/contribute-code/) and [Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/) where applicable. Yak Ops build tools and product boundaries remain repository-specific.

## Prerequisites and Commands

- Java 21, the checked-in Maven wrapper, and Git. Use Bash or WSL for the repository's `mvnw` wrapper on Windows.
- Backend verification: `bash mvnw -pl '!yak-ops-ui,!yak-ops-dist' verify`.
- Java formatting: `bash mvnw -DskipTests -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' com.diffplug.spotless:spotless-maven-plugin:3.10.2:apply`.
- Java format check: use the same command with `:check` instead of `:apply`.
- Frontend verification and tooling: `yak-ops-ui/docs/tooling.md`.

For environment setup and test scope, see [DEVELOPMENT.md](DEVELOPMENT.md). Only report checks that actually ran; a skipped integration test is not a pass.

## Repository Structure

- `yak-ops-core`: public, database-neutral flow contracts; must not depend on Runtime.
- `yak-flow`: local execution runtime and JDBC/CDC connectors.
- `yak-ops-business`: datasource and task-definition business operations.
- `yak-ops-plugins`: plugin APIs and implementations.
- `yak-ops-dao`: persistence, repositories and Flyway migrations.
- `yak-ops-platform`: security, workspace and user preferences.
- `yak-ops-common`: cross-module shared types and utilities.
- `yak-ops-boot`: HTTP boundary and application assembly.
- `yak-ops-ui`: frontend application and shared UI components.

Inspect the current code, direct callers, tests and Maven dependencies before changing a boundary. Derive current functionality from code, tests and supported public interfaces.

## Architecture Boundaries

- Do not introduce reverse dependencies from Core to Runtime or from business modules to Boot.
- Keep credentials out of task definitions and user-facing plugin parameters.
- Keep Workspace isolation, immutable task versions, migration history and existing safety checks intact.
- Do not claim exactly-once processing, distributed execution or product runtime integration without executable evidence.
- Do not add interfaces, factories, managers or compatibility layers without a real owner and caller.

## Common Change Patterns

- **Bug fix or internal refactor:** implement the smallest coherent change, update relevant tests and JavaDoc as needed. No routine Markdown synchronization.
- **New major subsystem or public contract change:** discuss and accept a YOIP before implementation. See [CONTRIBUTING.md](.github/CONTRIBUTING.md) and the [YOIP template](.github/YOIP_TEMPLATE.md).
- **Public interface:** assess REST, plugin SPI, persisted formats, checkpoint/state compatibility, configuration and monitoring consumers.
- **Database migration:** keep published Flyway migrations immutable; review upgrades, use forward-only migrations and test compatibility. Never use `flyway repair` to conceal a changed published checksum.
- **Frontend change:** follow `yak-ops-ui/FRONTEND_RULES.md`, TypeScript contracts and affected tests; apply the repository-wide YOIP process to significant public changes, and use focused PRs for routine UI work.

## Repository-Specific Invariants

- Keep task-definition identity, Workspace authorization and immutable definition versions in the business/persistence boundary. Plugin validation must not access credentials or silently rewrite requested parameters; accepted task configuration is JSON.
- Core owns Source/Sink and value contracts; Runtime owns physical graphs, tasks, mailbox scheduling and checkpoint execution. Connectors must not couple their runtime implementations to product Controllers/DAO.
- Keep published Flyway migrations frozen; draft migrations must be reconciled before the release migration gate. The release scripts verify names and sequence, not the semantic safety of SQL.
- Internal SLF4J log templates and Core / YakFlow technical diagnostics use English. Preserve exception types, causes and contextual fields; execution decisions must not parse exception message text.
- Product-facing ErrorCode descriptions, Bean Validation messages and other user messages remain localized at their owning boundary. Never expose raw internal diagnostics or credentials to users.
- Logging must not expose credentials or sensitive data. JavaDoc and implementation comments use English.
- Use meaningful top-level API contracts and explicit ownership; avoid redundant null-checking, normalization and compatibility layers. Current code, tests, and accepted public contracts determine supported behavior.

## Code Quality and Review

- Follow Flink's emphasis on clear ownership, readable code, explicit contracts and targeted tests; avoid redundant comments that repeat the code.
- Use the repository's existing Spotless + Palantir Java Format baseline until a separately reviewed tooling change is accepted. Do not switch formatters as a documentation side effect.
- Preserve Spotless, Checkstyle, ArchUnit, logging safety checks and real-database acceptance workflows. Never weaken or skip checks to make a PR green.
- Keep implementation facts in code, JavaDoc and tests. Avoid duplicating technical implementation descriptions in Markdown.
- For documentation, change published user guidance when behavior changes, not when an implementation detail changes. Do not create descriptive Capability/Contract/Architecture copies for a routine PR.
- Keep one issue or coherent change per PR and explain purpose, tests, public impact and any YOIP in the PR body.

## Persistence and Public Contract Documentation

- Document every newly introduced or changed persisted Entity field with concise English JavaDoc explaining its meaning, ownership, and lifecycle or safety semantics where relevant. Inherited audit fields retain their BaseEntity documentation.
- For new Flyway CREATE TABLE statements, include meaningful SQL COMMENT descriptions on every table and column. Explain identity, ownership, state, and null semantics where they matter. Do not modify an applied migration merely to add descriptions.
- Each declared Repository interface method needs JavaDoc for its contract, `@param` for every formal parameter, and `@return` for every non-void return type. Apply the same parameter and result documentation to newly exposed business Service methods and public Task plugin SPI methods. Document atomic updates, Workspace scoping and conflict behavior when relevant. Implementations inherit those method contracts and document only additional non-obvious behavior, rather than duplicating JavaDoc.
- Business service boundaries with independent callers use an interface and a corresponding Impl class, with transactional annotations on the managed implementation. Do not introduce interfaces for private helpers without independent contracts.
- Review these comments when adding Entities, Repository APIs, Service boundaries or Flyway migrations. Follow the existing formatting checks and do not create parallel comment-rules files.

## Current-State Comments and Documentation

- Describe current responsibilities, behavior, ownership, lifecycle, and invariants in the present tense. Comments and active rules must be understandable without implementation history or an upstream reference.
- Remove development-phase labels, previous-implementation narratives, and comparisons such as `PR1`, `legacy implementation`, `Flink-style`, or `unlike Flink` from ordinary JavaDoc, code comments, and active guidance. Keep design provenance in relevant PR or YOIP discussions.
- Preserve meaningful version numbers, persisted-format compatibility, safety constraints, recovery guarantees, and supported limitations. Express these as current behavioral contracts, not as reasons a compatibility layer was introduced.
- Keep FLIP references, migration analysis, and rejected alternatives in design proposals where they inform decisions. Published Flyway migrations and third-party notices are immutable or externally owned and are excluded from prose cleanup.
- Check rewritten descriptions against code and tests. Do not change public APIs, execution behavior, compatibility policies, or CI gates merely to improve wording.

## References

- [Development](DEVELOPMENT.md) — local setup and verification.
- [Contribution process](.github/CONTRIBUTING.md) — discuss, implement, review.
- [PR template](.github/PULL_REQUEST_TEMPLATE.md) — review expectations.
- [YOIP template](.github/YOIP_TEMPLATE.md) — major design proposals.
- [Flink Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/).
- [Frontend rules](yak-ops-ui/FRONTEND_RULES.md) — frontend ownership, behavior and UI conventions.
