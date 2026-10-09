# Yak Ops CI Status Contracts

These rules describe what a **successful GitHub Actions check** proves. They do not
modify repository branch protection or replace the existing release procedures.

## Pull-request quality gate

`.github/workflows/quality-check.yml` runs on every pull request and push to `main`.
It contains three independent quality jobs:

- `Backend Quality`: logging rules, format, compilation, Maven verification.
- `Frontend Quality`: formatting, lint, type checking, architecture, build.
- `Distribution Quality`: release distribution verification **when relevant files change**;
  otherwise a successful result means that distribution work was not required.

The `PR Required Checks` job depends on all three and uses `always()` so it
**fails if any upstream result is not `success`**, including `failure`,
`cancelled` or `skipped`. It does not rerun Maven/npm.

A green `PR Required Checks` is a quality check, **not JDBC/CDC E2E acceptance**.

## Backend acceptance is not yet available

`.github/workflows/backend-acceptance.yml` has two mutually exclusive jobs:

| Trigger | Job | Expected result |
| --- | --- | --- |
| Ordinary pull request or push to `main` | `Runtime E2E Status (not executed)` | Success **for accurate status reporting only** |
| Manual `workflow_dispatch` or scheduled run | `Full Runtime E2E Gate (unavailable)` | Failure |
| Release Gate calling `full_sweep: true` | `Full Runtime E2E Gate (unavailable)` | Failure |

The Release Gate remains fail-closed until the replacement JDBC/CDC runtime and
real database E2E tests are restored. Its PR-triggered run may therefore be red
when release-related workflows are edited; do not treat that red result as a
successful E2E run or disable the full-sweep requirement to make it green.

## Enabling enforcement on `main`

**A workflow file alone cannot enforce branch protection.** After this PR is
merged and the new check context has appeared on GitHub Actions:

1. Open repository **Settings → Rules → Rulesets** (or **Settings → Branches**
   for a classic branch-protection rule).
2. Create or update a rule targeting `main`. Require a pull request before
   merging and require status checks to pass.
3. Select the check named **`PR Required Checks`** from the root
   `Quality Check` workflow. Use the exact check context shown in a real PR;
   reusable-workflow callers may have a different qualified check name.
4. Require the branch to be up to date before merging if the repository uses
   strict checks. Review any allowed bypass actors; exempted administrators
   can otherwise still merge failing changes.
5. Validate with a deliberately failing quality check on a disposable PR:
   the check must fail and GitHub must block an unprivileged merge. Check that
   a normal green PR is allowed.

Do **not** configure `Runtime E2E Status (not executed)` as an E2E success
criterion. Path-filtered specialized workflows such as `YakFlow Core Runtime
Regression` are not guaranteed to run on every PR, so they should not be
blindly set as global required checks without accounting for skipped runs.

The existing `.asf.yaml` is not evidence that GitHub branch protection is
active. The linked GitHub integration cannot read the branch-protection
endpoint (403); current enforcement must be checked by a repository admin.
