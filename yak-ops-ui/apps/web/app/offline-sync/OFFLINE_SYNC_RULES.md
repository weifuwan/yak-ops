# Offline Sync Frontend Rules

Scope: `yak-ops-ui/apps/web/app/offline-sync/**` and the OFFLINE mode of `app/data-sync/task-editor.tsx`.

## Current Boundary

Both OFFLINE and REALTIME editors select one existing Source table and one existing Target table. The Task's Source/Target fields are the only editable table definition; do not submit `tableRoutes[]`, show a multi-table editor or duplicate a table list.
Historical multi-table Tasks are not silently converted to first-table Tasks.

- Keep the `数据源`, `数据来源`, `数据去向`, and `调度配置` sections; retain the fixed PageHeader, side navigation and content-only scroll.
- Source/Target Databases belong to the selected saved Datasources. Schema/Table selectors use the Datasource Catalog, with existing Select search and refresh.
- Keep OFFLINE write-mode selection under `数据去向` and the overwrite-risk Alert. Do not introduce mapping, automatic table creation, DDL preview or editable runtime tuning.
- Saving updates a single Task definition; Save & Publish composes Task save, optional Schedule save and Publish without running.
- The Task list owns explicit Publish/Unpublish, manual Run and Schedule enable/disable. Task Detail owns task-filtered history; Operations Center owns cross-Task Dashboard.
- Use existing Yak UI components, selectors and responsive Datasource cards. Never add redundant explanatory copy; show only validation and meaningful risk warnings.
- Runtime is not yet wired to product execution. Do not invent successful execution or progress data in the UI.

## Scheduler and History

- Cron and Time Zone are separate Schedule definitions. Unconfigured Schedule means manual-only. Saved Schedule is not automatically enabled.
- Time Zone uses Select and defaults to `Asia/Shanghai`; the next five firings come from the backend Preview API.
- Execution / Attempt history, frozen snapshots and diagnostics are read-only persisted facts. No UI logic may derive or fabricate runtime metrics.
- Preserve Workspace isolation, existing HTTP endpoints, schedule controls and task definition-version semantics.
