# Yak Ops CI Status Contracts

## Pull requests: one Quality Check workflow

`.github/workflows/quality-check.yml` is the **only automatic pull_request**
workflow. It also runs on pushes to `main` and can be reused by the formal
Release Gate via `workflow_call`.

There are four stable jobs; no extra change-detection job:

| Job | Required work when in scope |
| --- | --- |
| `Backend Quality` | Backend boundaries (Core does not import Runtime, deleted legacy packages stay absent), release scripts/metadata, logging, Spotless, **one Maven verify** |
| `Frontend Quality` | npm ci, format, lint, typecheck, architecture, build |
| `Distribution Quality` | Full distribution build and verification for release/package-related changes |
| `PR Required Checks` | `always()` aggregate; all three preceding jobs must return `success` |

`scripts/ci/detect-changes.sh` runs as a **step in each existing quality job**
and only skips the expensive setup/build/test steps for unrelated file changes.
Every job still runs and reports an explicit scope decision:

- Java/Backend/Core/YakFlow changes run Backend Quality including **the full
  reactor Maven `verify`**. This also runs YakFlow Core/Runtime tests; it
  replaces the old duplicate YakFlow and Data Sync testing workflows.
- Frontend changes run Frontend Quality.
- Distribution/release packaging changes run Distribution Quality.
- Root workflow/CI script changes, unclassified file paths and non-PR/non-push
  events run **all three** (safe fallback). A formal `workflow_call` from the
  Release Gate always exercises the full quality suite, never selective checks.
- Documentation-only changes may skip heavy jobs. The scope steps still run
  and `PR Required Checks` remains present and required.
- A malformed diff or scope detection failure fails its job; it cannot result
  in a silently green required status.

The old `YakFlow Core Runtime Regression` and `Data Sync Legacy Removal
Verification` Workflow files were removed **only after** moving their
architecture/legacy assertions into Backend Quality. Their Maven `test`
invocations were redundant with the existing backend `verify`; no test cases
were removed.

A green `PR Required Checks` means **code quality passed for the relevant
change scope**. It does **not** mean JDBC/CDC integration E2E passed.

## Backend acceptance and publishing

The backend acceptance entry point remains at
`.github/workflows/backend-acceptance.yml`, but it **does not run on ordinary
PRs or main pushes**. Manual dispatch and the Release Gate's
`workflow_call(full_sweep=true)` continue to fail closed until real
JDBC/CDC runtime integration E2E exists. The informational status job is not
evidence of an integration test.

`.github/workflows/v1-release-gate.yml` now runs only via
`workflow_dispatch`, not for changes to release scripts on ordinary PRs.
It still reuses **full** Quality Check (no path-based skip), requires Full
Backend Acceptance, tests distribution/Docker/Compose, checks release
readiness and manual E2E evidence. `release-publish.yml` remains manual
and enforces the matching formal Gate run and commit SHA.

## Enforcing the stable status check on `main`

**A workflow file alone cannot enforce branch protection.** After this PR
is merged, an administrator should verify the GitHub Ruleset / branch
protection settings:

1. In repository **Settings → Rules → Rulesets** (or **Settings → Branches**),
   target `main`, require pull requests and passing status checks.
2. Make **`PR Required Checks`** (from root `Quality Check`) required.
   Use the exact GitHub check context shown on a real PR.
3. **Remove any old required checks** that pointed to the now deleted
   `YakFlow Core Runtime Regression` or `Data Sync Legacy Removal
   Verification` workflows. Otherwise unrelated PRs may be stuck waiting
   for check contexts that never run.
4. Consider strict up-to-date checks and review bypass actors. Verify a
   deliberate failing-check PR cannot be merged without a permitted bypass.

Do **not** require a now-removed path-filtered workflow or unavailable
runtime-E2E status job as a global success gate. Formal release acceptance
remains independent and strict.

The linked GitHub App cannot read legacy branch protection (403); the
current enforcement configuration must be confirmed by a repository admin.
