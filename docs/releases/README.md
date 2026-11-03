# Yak Ops Releases

Yak Ops 每个正式版本都有一份独立的、经 Review 的发布说明。此目录记录**该版本 Tag 对应的产品行为**，不是当前 `main` 分支的能力清单。

| Version | Released | Release Notes | GitHub Release |
| --- | --- | --- | --- |
| v1.4.0 | Pending | [v1.4.0](v1.4.0.md) | Pending manual publication |
| v1.3.0 | 2026-10-22 | [v1.3.0](v1.3.0.md) | [Release](https://github.com/weifuwan/yak-ops/releases/tag/v1.3.0) |
| v1.2.0 | 2026-10-07 | [v1.2.0](v1.2.0.md) | [Release](https://github.com/weifuwan/yak-ops/releases/tag/v1.2.0) |
| v1.1.0 | 2026-10-04 | [v1.1.0](v1.1.0.md) | [Release](https://github.com/weifuwan/yak-ops/releases/tag/v1.1.0) |
| v1.0.0 | 2026-09-29 | [v1.0.0](v1.0.0.md) | [Release](https://github.com/weifuwan/yak-ops/releases/tag/v1.0.0) |

## Rules

1. **One release, one file:** `docs/releases/vX.Y.Z.md`, based on [TEMPLATE.md](TEMPLATE.md). No duplicate full-size `CHANGELOG.md`, release contract or readiness copy.
2. **Keep history:** do not delete, rename, or silently rewrite published release notes. Correct material mistakes with a dated **Errata** entry, citing the published version and correction.
3. **Review before publishing:** the complete notes must be committed and reviewed **before** a release. They describe what the candidate commit actually ships, not a YOIP proposal or a later `main` state.
4. **Write for users:** explain the outcome of a change, not class renames or a raw PR/commit list. Group changes under New Features, Improvements, and Bug Fixes. Link significant claims to PRs or YOIPs.
5. **Be explicit about compatibility:** Breaking Changes, Upgrade Guide, Compatibility and Known Limitations are required even when their value is `None`. Document migration preconditions, state/offset handling, supported sources/targets and deployment requirements when relevant. Do not claim unverified compatibility.
6. **Preserve version identity:** version in the filename/title must match the repository version, Git Tag, distribution and immutable Docker tags.
7. **Only facts:** do not claim a test passed unless an actual run and preserved evidence proves it. Record the test command, commit SHA and result; a formatting check does not certify a release.
8. **Lightweight PRs:** ordinary internal refactors need no release Markdown changes. User-visible features, behavior changes, operational changes and breaking changes must be reflected in the next release notes.
9. **Single publication source:** use reviewed, checked-in notes as the GitHub Release text when publishing manually; do not regenerate them from an unreviewed PR list.

## Maintainer Workflow

- Prepare the next `docs/releases/vX.Y.Z.md` from the template and add it to this index before the release candidate is frozen.
- Use `bash scripts/release/check-release-notes.sh X.Y.Z` and run applicable local release validation. GitHub CI only checks formatting and backend tests.
- Review installation/upgrade, migration safety, versioned compatibility, and limitations against the candidate commit.
- Follow [manual release verification](../../scripts/release/README.md); preserve approval and build evidence with the release.
- Add an Errata section with date and source for any later factual correction. Do not move tags or modify prior build artifacts.

Earlier detailed release plans/readiness documents remain available in the immutable version Tags (for example [v1.2.0 historical release directory](https://github.com/weifuwan/yak-ops/tree/v1.2.0/docs/release)). The restored notes distill the **released** product behavior without resurrecting outdated workflow instructions.
