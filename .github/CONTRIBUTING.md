# Contributing to Yak Ops

Yak Ops currently maintains two independent modules: `yak-ops-cdc` and `yak-ops-ui`. Follow [DEVELOPMENT.md](../DEVELOPMENT.md) for scoped build and formatting commands.

- Discuss public CDC interfaces and compatibility changes before implementation. Preserve the Flink CDC upstream license/attribution and its defined event, source, composer and sink contracts.
- Keep UI changes focused; do not assume the removed Yak Ops backend exists.
- Describe behavior, risk and verification evidence in a focused pull request. Report skipped or blocked tests, not inferred successes.
- The [YOIP template](YOIP_TEMPLATE.md) remains available for major design proposals; no YOIP validation CI is enabled.
- Update documentation when user-visible behavior or supported build instructions change.
