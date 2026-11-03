# Contributing to Yak Ops

Yak Ops follows the Apache Flink [Contribute Code](https://flink.apache.org/how-to-contribute/contribute-code/) process and [Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/). Yak Ops records proposals, discussions and reviews through GitHub.

## Discuss, Implement, Review

1. Discuss substantial changes and agree on the scope before writing code. Routine fixes and internal refactors may be explained directly in a GitHub PR.
2. Use a YOIP for major new features, subsystems or changes to public contracts. Have the design accepted before beginning a series of implementation PRs.
3. Keep each implementation PR focused. Link the accepted YOIP when applicable; explain the motivation, solution, tests and compatibility impact.
4. Follow [DEVELOPMENT.md](../DEVELOPMENT.md), run checks for the changed scope and resolve review feedback before merge. A passing unit test must not be presented as passing runtime acceptance.

## Yak Ops Improvement Proposals (YOIPs)

Use the [official Flink FLIP process](https://cwiki.apache.org/confluence/spaces/FLINK/pages/65145551/Flink+Improvement+Proposals) and [official FLIP template](https://cwiki.apache.org/confluence/spaces/FLINK/pages/65145553/FLIP+Template) as the upstream model. Yak Ops records its own decisions as YOIPs using proposal PRs; the local [YOIP template](YOIP_TEMPLATE.md) retains all six original sections. Related Flink FLIPs may be linked as design references, not as claims of completed implementation or full compatibility.

All YOIPs must be written in English, including titles, sections, tables, examples, and later updates. Follow the language rule in the [YOIP template](YOIP_TEMPLATE.md) without translating API identifiers or other literal technical values.

A YOIP is appropriate for a significant new subsystem, externally supported API, plugin SPI, serialized state/checkpoint format, persisted task-definition format, user-facing configuration, monitoring interface, deployment contract or significant compatibility change. Small internal refactors, bug fixes and class/package reorganizations do **not** require a YOIP.

### YOIP Admission and Historical IDs

One significant design proposal may guide multiple implementation PRs. Do **not** assign a new YOIP to every CDC source/sink step, bug fix, class rename, CI repair, acceptance run or internal refactor. Put implementation details, test evidence, and rollout progress into focused PRs, tests, JavaDoc or release documentation. A material new public guarantee requires a new discussion, not silent edits to accepted design history.

Use the [YOIP index](../docs/yoips/README.md) as the proposal register. Historical identifiers YOIP-7 through YOIP-19 are retired and reserved, even after their navigation files are deleted. The duplicate historical CDC YOIP-4 label is not an approved proposal; YOIP-4 belongs to the accepted Quartz design. Historical implementation details remain accessible through Git history and original PRs, not a second engineering-document directory. New proposals must be registered in the index and use the exact six English template sections in order, with substantive content rather than placeholders.

Maintainers can run `python3 scripts/ci/check_yoips.py` when reviewing YOIP records. It validates syntax, index registration and decision links; it cannot verify who voted or evaluate design quality. Normal implementation PRs do not need to change a YOIP.

### Propose and Discuss

- Create a **proposal-only** PR containing `docs/yoips/YOIP-N-short-title.md`, based on [YOIP_TEMPLATE.md](YOIP_TEMPLATE.md), and add its entry to the [YOIP index](../docs/yoips/README.md). Check open proposals and reserve an unused number above the historical 7–19 range. A proposed YOIP is not an implemented feature.
- Start with `Status: Discussion` and `Decision: Pending`. After opening the proposal PR, replace the Discussion placeholder with its actual GitHub PR URL. The YOIP validation script can fail until the PR URL is recorded.
- Title the proposal PR `[DISCUSS][YOIP-XXXX] Description`. Link the proposal to the PR discussion and record the status as `Discussion`.
- Discuss motivation, public interfaces, compatibility and rejected alternatives **on the proposal PR**, not in implementation commits. Update the proposal to reflect material design changes.
- When outstanding design questions are resolved, request an explicit maintainer **[VOTE]** / decision in the proposal PR. Record the acceptance or rejection and its rationale there; do not infer acceptance from silence or a green CI status.
- For acceptance, link the actual maintainer `[VOTE] Accepted` comment from the same proposal PR in the `Decision` field, update the proposal index, then change status to `Accepted` and merge after maintainer review. Link it from subsequent implementation PRs. Rejected proposals may remain as closed PR discussions; do not merge a rejected design into active requirements.
- Release notes and executable verification evidence belong with the relevant implementation/release, not in the YOIP's ongoing progress log. Major changes to an accepted public design need renewed discussion, typically a new YOIP.

**Platform adaptation:** This repository currently has GitHub Issues and Discussions disabled. Therefore proposal PRs provide the discussion and decision record; this does not claim an Apache mailing-list vote or Apache committer consensus. If those GitHub features are enabled later, the discussion venue can be changed without changing the YOIP criteria or template.

## Documentation Policy

`docs/yoips/` is the sole location for lasting architecture proposals. Do not recreate `docs/engineering/` or maintain per-PR technical diaries as a second documentation hierarchy. Use code, tests, JavaDoc, implementation PRs and their discussion for implementation facts; preserve accepted YOIP decisions as historical design records rather than continuously rewriting them as current implementation guides.

Versioned user-facing release notes follow [docs/releases/README.md](../docs/releases/README.md). Ordinary internal refactors do not require a release-note entry; public behavior, compatibility and operational changes do.

Code, types and tests describe the implemented behavior. Update JavaDoc for contracts that callers cannot infer; update user guides when visible behavior, compatibility or operations change. Do **not** require updates to Architecture, Capability or module Rules Markdown solely because a class, method or package changed. Do not create new descriptions of existing implementation just to satisfy a PR checklist.

Use code, JavaDoc, TypeScript contracts, tests, persisted data and accepted YOIP decisions for current behavior. The same YOIP threshold applies to frontend and backend public changes; ordinary UI layout, styling and internal component refactors use focused PRs without separate descriptive design documents. Preserve security, migration checks and manual release validation. Write YOIPs for significant proposed designs, not routine changes.

## Pull Requests

Use the [PR template](PULL_REQUEST_TEMPLATE.md). Assess dependencies, API/format compatibility, hot-path performance, checkpoint/recovery, migrations and security if applicable. Explain what was tested and what was **not** tested. AI-assisted contributions must be reviewed and verified by the author; disclose AI assistance in the PR.
