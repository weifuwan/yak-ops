# Operations Center Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/operations/**`
- shared runtime surfaces reused by Operations Center under `yak-ops-ui/apps/web/app/data-sync/**`

## Responsibility Boundary

Operations Center owns cross-Task operational observability. Data Integration owns Task definition / publication plus Task-scoped read-only runtime detail.

Both Data Sync roots are aggregate Dashboards:

- OFFLINE Dashboard observes bounded Execution volume, result and failure trends.
- REALTIME Dashboard observes active Task count, runtime state, automatic recovery and failure trends.

Must:

- Keep OFFLINE and REALTIME data isolated by `syncType`.
- Dashboard pages read only `POST /api/v1/data-sync/operations/dashboard`; do not fetch Instance pages and aggregate them in browser memory.
- Range is limited to TODAY / LAST_7_DAYS / LAST_30_DAYS. TODAY renders hourly buckets; 7 / 30 days render daily buckets exactly as supplied by backend.
- Reuse the local thin `EChart` integration for ECharts lifecycle: init / setOption / ResizeObserver / dispose. Product charts own option semantics; Yak UI does not depend on ECharts.
- Use Yak UI `Card` only as a neutral visual surface. Card must not learn Data Sync metrics or ECharts options.
- OFFLINE charts are limited to persisted metrics the backend can prove: read/write volume trend, execution result trend, execution status distribution and FAILED + LOST Task Top 5.
- REALTIME charts are limited to Execution creation trend, Execution status distribution, AUTO_RECOVERY trend and FAILED + LOST Task Top 5.
- REALTIME Summary Cards use currentActiveTaskCount, abnormalTaskCount, autoRecoveryCount and FAILED + LOST Execution totals from the backend read model.
- Do not derive Retry totals by summing Attempt metrics; dashboard readRows / writeRows already follow the backend Execution current/final Attempt mirror semantics.
- Do not invent realtime throughput, events/s, CDC Lag or Checkpoint Lag from cumulative counters. Those require a persisted Metrics Time Series.
- Dashboard roots are observability-only: they do not render Task definition tables, Instance history Tabs, Run / Start / Stop buttons, Schedule controls or Desired State controls.
- Existing Operations Instance detail routes remain compatibility routes. They may still render the shared Execution detail and active-instance Stop behavior, but Dashboard pages do not link to them as primary navigation.
- Compatibility detail routes return to the relevant Operations Dashboard rather than a removed Instance Tab.
- Preserve Workspace scoping through the Operations Center AppLayout and backend read model.

Must Not:

- Edit Task definitions in Operations Center.
- Publish / unpublish Tasks in Operations Center.
- Recompute backend dashboard buckets, status zero-fill or failure ranking in frontend code.
- Build a second chart framework or wrap the entire ECharts API into Yak UI props.
- Put runtime history inside the Task Editor; Task Detail is a separate read-only surface.
