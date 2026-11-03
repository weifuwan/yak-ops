# Contributing to Yak Ops

Yak Ops follows the Apache Flink [Contribute Code](https://flink.apache.org/how-to-contribute/contribute-code/) process and [Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/). Discuss designs, implementation and reviews through GitHub pull requests.

## Discuss, Implement, Review

1. Discuss substantial changes and agree on scope before writing code. Routine fixes and internal refactors may be explained directly in a GitHub PR.
2. For major subsystems, public interfaces, persisted formats or significant compatibility changes, document the motivation, proposed design, alternatives and compatibility impact in a focused design discussion or PR before implementation.
3. Keep each implementation PR focused. Link relevant design discussions when applicable, and explain the solution, tests and compatibility impact.
4. Follow [DEVELOPMENT.md](../DEVELOPMENT.md), run checks for the changed scope and resolve review feedback before merge. A passing unit test must not be presented as passing runtime acceptance.

The [YOIP template](YOIP_TEMPLATE.md) remains available as an optional design-writing reference; there is no maintained YOIP index or YOIP validation gate. Earlier proposals remain accessible through the Git history and their original pull requests.

## Documentation Policy

Do not recreate `docs/engineering/` or maintain per-PR technical diaries as a second documentation hierarchy. Use code, tests, JavaDoc, implementation PRs and their discussions for implementation facts and design decisions.

Versioned user-facing release notes follow [docs/releases/README.md](../docs/releases/README.md). Ordinary internal refactors do not require a release-note entry; public behavior, compatibility and operational changes do.

Code, types and tests describe implemented behavior. Update JavaDoc for contracts that callers cannot infer; update user guides when visible behavior, compatibility or operations change. Do **not** require updates to architecture, capability or module rules Markdown solely because a class, method or package changed. Do not create new descriptions of existing implementation just to satisfy a PR checklist.

Use code, JavaDoc, TypeScript contracts, tests, persisted data and linked PR discussions for current behavior. The same review standards apply to frontend and backend public changes; ordinary UI layout, styling and internal component refactors use focused PRs without separate descriptive design documents. Preserve security, migration checks and manual release validation.

## Pull Requests

Use the [PR template](PULL_REQUEST_TEMPLATE.md). Assess dependencies, API/format compatibility, hot-path performance, checkpoint/recovery, migrations and security if applicable. Explain what was tested and what was **not** tested. AI-assisted contributions must be reviewed and verified by the author; disclose AI assistance in the PR.
