# Playwright UI Tests

Playwright checks browser interaction, rendering, validation, navigation, and failure states. Database writes, CDC transitions, checkpoint correctness, scheduler admission, and Task lifecycle belong to backend JUnit integration tests.

| Layer | Technology | When |
| --- | --- | --- |
| Unit and frontend contracts | JUnit / TypeScript | Every PR |
| Backend integration and E2E | JUnit + Docker | Related PR and release |
| UI automation | Playwright | Release; manual as needed |
| Deployment smoke | Docker Compose | Release |

The suite keeps stable business scenarios instead of adding a new script for every feature PR:

- `data_sync.py`: solution selection, draft restoration, multi-table mapping and selection/removal, schema preparation configuration, realtime stage observations, partial schedule save/retry, and parameter/publication history
- `scheduling.py`: schedule history, navigation, responsive mode, conflicts, Workspace isolation
- `login.py`: real login and UI navigation through the candidate Compose images
- `mock_api.py`: deterministic browser-only API fixtures
- `run.py`: isolated browser context, bounded waits, trace and failure screenshot, versioned report

Manual `Playwright UI Tests` workflow serves a built frontend and runs the mocked suite. The formal Release Gate runs **full** on the built Compose images, including login through nginx. Only test framework PRs trigger an additional automatic UI CI run; ordinary PRs do not.

```bash
python3 -m pip install playwright==1.57.0
python3 -m playwright install chromium
# Serve the built yak-ops-ui on localhost:5173 first.
python3 yak-ops-ui/tests/e2e/run.py --suite mocked --base-url http://127.0.0.1:5173
```

The `version-publication` scenario checks that canceling a drawer discards only its local changes, applying a drawer does not save, online saves retain the published version and Debug setting, and historical parameters can be viewed, compared, and restored into the editor before an explicit save and publication. It also checks Debug-only publication without an extra parameter version, unchanged historical instances, dirty-page navigation protection, and version-query navigation while the editor is dirty. These checks use mocked HTTP responses and do not establish backend transaction correctness.

The `batch-table-options` scenario covers the single-table body: source WHERE and split-key intent, preview failure/retry and exact numeric display without task writes, ordered pre/post SQL, MySQL REPLACE selection, type mappings, per-table engine settings, and schema 8 save/reopen. The `replication-table-options` scenario covers paginated table transfer, table-dialog cancellation, snapshot and DML settings, selective bulk edits, and explicit confirmation before switching to regex selection. It checks saved regex patterns and naming/default rules, plus a controlled delayed match that prevents editing rows while their replacement is pending and prevents matching while a row dialog is open. Actual discovery, SQL transactions, and recovery remain backend integration contracts. Existing mapping and field-expression scenarios use the same current dialogs and retain duplicate-target, hidden-key reset, draft, and publication assertions.

Playwright's pinned Chromium remains the default for local runs and formal CI. For a local environment where that browser cannot be installed, an explicitly selected compatible Chromium executable can be used:

```bash
python3 yak-ops-ui/tests/e2e/run.py --scenario version-publication \
  --base-url http://127.0.0.1:5173 \
  --browser-executable /absolute/path/to/chrome
```

Evidence appears at `/tmp/yak-playwright/v<VERSION>/<SHA>/playwright-results.json`, with traces for mocked scenarios and failure screenshots. Each scenario writes `browser.json`; the suite report includes the actual browser version, Playwright version, executable path, and whether the browser came from the pinned installation or an explicit executable. An alternate-browser run must be reported with that identity and does not replace the pinned-browser release gate. Live login intentionally does not record a trace to avoid storing a typed test password. Reports must record both release version and exact commit SHA. A skipped or failed Playwright suite does not pass release tests.

Tests must be isolated, use role/label locators, controlled fixtures, condition-based waits, and bounded timeouts. No arbitrary sleep, real CDC/Cron wait in the browser, implicit mutable shared state, or retry solely to hide flakes.
