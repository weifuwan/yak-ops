# Yak Ops AI Agent Instructions

Project rules for humans and coding agents. Follow [Apache Flink's contribution](https://flink.apache.org/how-to-contribute/contribute-code/) and [code-quality](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/) guidance where applicable; Yak Ops contracts and tooling take precedence. Keep these rules short; load linked documents only when relevant to the task.

## Prerequisites and Commands

- Java 21, Git, and the checked-in Maven wrapper. On Windows, run `mvnw` through Bash/WSL.
- Full local backend verification (including CDC, opt-in): `bash mvnw -pl '!yak-ops-ui,!yak-ops-dist' verify`.
- Automatic backend CI runs native Yak Ops unit tests and Business database IT only; it excludes all `yak-ops-cdc` modules from the Maven unit-test reactor. Run CDC tests explicitly with `bash scripts/tests/run.sh cdc` or the manual Backend Tests workflow.
- Java formatting: `bash mvnw -DskipTests -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' com.diffplug.spotless:spotless-maven-plugin:3.10.2:apply`; replace `:apply` with `:check` to verify.
- See [DEVELOPMENT.md](DEVELOPMENT.md) for scoped checks, real-database suites, and review evidence; [frontend tooling](yak-ops-ui/docs/tooling.md) for UI commands.

## Agent Working Protocol

- **Evidence before assumption:** Read current code, callers, tests, configuration, and dependencies before claiming behavior. Trace execution and state transitions rather than relying on names or comments. Separate verified facts, assumptions, and recommendations; cite paths and line numbers for findings.
- **Understand before edit:** Identify entry points, ownership, lifecycle, supported contracts, and affected consumers. Search for existing implementations before creating types or abstractions. Analysis/review requests are read-only; do not turn a discussion into an implementation or PR.
- **Root cause before fix:** Locate the first actionable failure. Distinguish production defects, tests, configuration, and infrastructure. Never suppress a failure by weakening assertions, skipping checks, silently swallowing errors, or changing unrelated production behavior.
- **Minimal complete change:** Prefer the smallest coherent solution, not merely the fewest lines. Replace the authoritative execution path completely; delete obsolete code and tests when safe, and update only affected callers and documentation. Do not broaden scope into speculative cleanup.
- **Verification before completion:** Run the narrowest meaningful checks first; widen scope for shared contracts, persisted state, and recovery behavior. State exactly what ran, passed, failed, was skipped, and remains uncertain. No unexecuted test counts as passed.
- **Authorization and reporting:** Do not create PRs, merge, change branch protection, or mutate repository settings unless requested. Keep findings actionable and concise. If evidence overturns an earlier conclusion, correct it.

## Repository Structure

- `yak-ops-cdc` retains Flink CDC CLI, composer, runtime and MySQL connector source; it is not yet wired to the product Task plugin.
- `yak-ops-common` now owns shared `io.yak.ops.configuration` option parsing and validation contracts used by Task and Datasource plugins; do not recreate a separate configuration module.
- `yak-ops-business` owns datasource/task business lifecycle; `yak-ops-task-execution` owns process-level dispatch, observation, cancellation, and embedded Task execution.
- `yak-ops-plugins` owns plugin APIs and implementations; `yak-ops-dao` owns repositories, persistence, and Flyway; `yak-ops-platform` owns Workspace, security, and preferences.
- `yak-ops-common` holds genuine shared utilities; `yak-ops-boot` assembles the application and HTTP boundary.
- `yak-ops-registry-api` and `yak-ops-registry-jdbc` retain upstream DolphinScheduler 3.4.3 Registry source and API; keep upstream files and source formatting intact, isolate only minimal compatibility adapters and document deviations.
- `yak-ops-ui` owns the frontend; `yak-ops-test-utils` is test-only.

## Architecture Boundaries

- Do not add reverse dependencies from business modules to Boot or product Task components to CDC connector internals. Connectors must not depend on Controllers/DAO or product Task identity for runtime creation.
- Preserve Workspace authorization and isolation, immutable task versions, task-definition identity, credential separation, and safety checks. Accepted task configuration is JSON; plugin validation must not read credentials or silently rewrite user parameters.
- Keep Reader/Writer mutable execution state separate from immutable checkpoint snapshots. Preserve checkpoint, serializer, handoff, offset, and recovery guarantees; do not claim exactly-once or distributed behavior without executable evidence.
- Logging and diagnostics must not expose credentials, sensitive row data, dispatch tokens, or state bytes, including via `toString()`. Preserve exception types and causes; never derive execution decisions from exception message text or expose raw internal diagnostics to users. Technical logs and JavaDoc use English; user-visible errors are localized at their owning boundary.

## Minimal Implementation and Compatibility

- Maintain one current execution path. Do not keep duplicate protocols, deprecated implementations, alias options, forwarding wrappers, or fallback paths without a supported consumer.
- Compatibility requires a documented published contract, named consumer, maintainer decision, and removal condition. Explicitly reject incompatible persisted definitions/checkpoints; never silently reinterpret running state or downgrade guarantees.
- Prefer established Flink, Flink CDC, and SeaTunnel classes, lifecycle, naming, and algorithms **where semantics match**. Verify upstream version and dependencies; adapt real Runtime or Debezium differences explicitly. Do not copy a name while changing its contract.
- New interfaces, factories, helpers, configuration options, branches, and defensive checks need a real caller, owner, and failure case. Keep fail-closed validation and resource/data safety.

## Java Type Design and Naming

- Organize packages by ownership (`source`, `reader`, `state`, `execution`, `repository`), not mechanical categories. Use `domain` for business concepts and `model` only for a cohesive owned model set; avoid one-type packages and synonym DTO chains.
- Interfaces express an independent caller/implementation contract; abstract classes share actual behavior/state. Contexts carry one stage's inputs/capabilities, not cross-layer service locators. Resource wrappers must represent real ownership.
- **Do not introduce production Java `record` declarations.** Existing records are grandfathered; preserve equality, hashing, ownership, serializer formats, and sensitive-data protection when changing them.
- **Do not use Java `var` in new or modified Yak Ops production code.** Declare explicit local-variable and enhanced-for element types. Existing unrelated uses can be removed gradually; DATA_SYNC production has no `var` declarations.
- Defensively copy mutable arrays and collections at ownership boundaries, especially when creating checkpoint snapshots.
- **Do not introduce `Codec` in project-owned Java type, method, or package names.** Use precise terms such as `Parser`, `Serializer`, `Mapper`, and `Converter` when applicable; do not rename third-party identifiers.
- `normalize` means canonicalization, not parsing, validation, resolution, planning, or applying state. Reserve `*Gate` types for Runtime network I/O boundaries (`InputGate`); do not use `Manager`, `Handler`, `Compiler`, or `Policy` as vague substitutes for an actual responsibility.
- Avoid redundant package-local prefixes (for example, `Sync*` under `io.yak.ops.plugin.task.datasync`). Preserve meaningful `Jdbc` and `MySqlCdc` qualifiers where they disambiguate public APIs.
- Keep shared helpers with the module owning their invariant. Naming-only changes must not alter published identifiers, JSON fields, configuration keys, or checkpoint formats; do not add aliases merely to preserve internal names.
- `python3 scripts/ci/check_backend_naming.py` enforces new Java records, `Codec` symbols, invalid native `*Gate` types and new `var` declarations in owned Yak Ops Java (existing declarations are grandfathered). Semantic ownership still requires review.

## Code Quality and Review

- GitHub CI runs frontend Oxfmt, backend Spotless, and native backend unit/Business integration tests. CDC tests are manual-only; formatting still covers changed CDC Java. Other static analysis and build commands remain available locally; never claim an unrun check passed.
- Test externally observable contracts and invariants, not every new method or class. Do not expose production accessors just for tests. Prefer deterministic assertions to sleeps, reflection into private state, and method-count mocks.
- Ordinary bug fixes/refactors need focused code/tests/JavaDoc, not parallel Markdown narratives. Use [the YOIP process](.github/CONTRIBUTING.md) before major subsystem or public contract changes; routine CDC stages, CI fixes, and refactors do not need YOIPs.
- Frontend changes follow [FRONTEND_RULES.md](yak-ops-ui/FRONTEND_RULES.md). Keep one coherent change per PR and report its purpose, public impact, and evidence using [the PR template](.github/PULL_REQUEST_TEMPLATE.md).

## Verification Scope and Incremental Review

- Ordinary `verify` must not start Docker. Use `scripts/tests/run.sh` and its suite manifest for Business and MySQL CDC database integration tests. CDC tests are excluded from automatic CI, not deleted. Keep registered IT reports complete; missing, skipped, or failed selected tests are failures.
- Select unit tests for pure logic, real database IT for current CDC and Task persistence semantics, and targeted CDC recovery integration tests for durable guarantees. Do not repeat the same scenario for every class/database. Retain unique dialect, XA, security, checkpoint, and crash-recovery coverage; map coverage before removing tests.
- Isolate data, Workspace, and state per test. Use bounded condition-based waits; do not hide flakes via silent retries or change production checkpoint intervals for tests. Destructive integration tests need private environments and captured diagnostics before cleanup.
- Follow the [Backend Test Selection](DEVELOPMENT.md#backend-integration-tests) scope. Frontend CI checks formatting only, not rendering, types, builds, or API behavior. Unselected suites are **not selected**, not passed.
- For reviews, record head, base, tested tree/SHA, changed files, and relevant CI evidence. Read affected callers/tests; after fixes review the delta from the prior reviewed SHA, expanding when shared contracts or base change. Known unresolved failures remain blockers.

## Persistence and Public Contract Documentation

- Treat the v1.3.0 fresh-install Flyway baseline and subsequent published migrations as immutable. New approved changes use forward-only migrations; never conceal incompatible history via `flyway repair`.
- Document new/changed persisted Entity fields and new Flyway tables/columns with meaningful English JavaDoc/SQL comments. Document each declared Repository method (`@param` and non-void `@return`), and newly exposed business Service and public Task plugin SPI contracts, including Workspace/transaction semantics.
- Business Service boundaries with independent callers use an interface and managed Impl with transaction ownership; avoid interfaces for private helpers.

## Current-State Comments and Documentation

- Describe current responsibility, lifecycle, invariants, and supported limitations in present tense. Do not leave development-phase labels, PR numbers, obsolete implementation narratives, or comparison commentary in ordinary code/JavaDoc.
- Keep upstream rationale, FLIP comparisons, and rejected alternatives in YOIPs/PR discussions. Do not duplicate implementation facts in Markdown; update published user guidance when user-visible behavior changes.
- Do not change public behavior, persisted formats, existing safety gates, or immutable third-party/release notices just to simplify prose.

## References

- [Local development and verification](DEVELOPMENT.md)
- [Contributing and YOIP governance](.github/CONTRIBUTING.md)
- [YOIP index](docs/yoips/README.md)
- [Frontend rules](yak-ops-ui/FRONTEND_RULES.md)
- [OpenCode project commands](.opencode/commands/) — invoke targeted analysis/review workflows on demand rather than embedding them in these always-loaded rules.
