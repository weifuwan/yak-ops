# Offline Sync Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/offline-sync/**`
- OFFLINE mode of `yak-ops-ui/apps/web/app/data-sync/task-editor.tsx`

## Current Boundary

v1.3 PR4 adds OFFLINE Multi-Table Editor inside the existing Task Editor sections. OFFLINE supports 1-50 explicit Source Tables sharing one Source Datasource / one Target Datasource. REALTIME keeps its existing single-table editor.

Must for Multi-Table:

- Select multiple Source Catalog tables, without full database wildcard or automatic discovery.
- Persist and hydrate stable `tableRoutes[].id`, maintaining per-route Mapping / Target / Auto Create; editing an existing Route must preserve its ID.
- Under `数据去向`, show each Source → Target mapping. Auto Create disabled uses Catalog Select; enabled uses controlled Input; both must not appear simultaneously for the same Route.
- Under `去向字段映射`, switch among the selected Routes and reuse `SchemaMappingEditor`; each Route has its own backend Schema Preview / DDL and backend-compatible status.
- Limit Schema Preview requests to bounded concurrency; avoid N simultaneous unbounded network requests.
- Disable Save until every selected Route's latest backend preview is compatible and target paths do not duplicate.
- Preserve Task-level write mode, scheduling and publication controls; do not introduce per-table Runtime tuning, retry or scheduler configuration.
- Do not add generic helper/subtitles. Only validation blockers, dangerous write modes and necessary state semantics may render Alert content.

The offline product owns Task definition, publication configuration, Schedule definition, Schedule enable / disable on the Task list, a manual Run shortcut, and Task-scoped read-only runtime detail. Operations Center remains the cross-Task observability surface; Execution Stop is not moved into Offline Sync definition pages.

Must:

- Reuse saved Datasource resources; never ask for database credentials.
- Keep resource selection and table configuration as separate editor layers: the `数据源` section owns source/target Datasource selectors; `数据来源` owns Schema/table selection; `数据去向` owns Schema/table selection plus OFFLINE write mode.
- Render source and target Datasource cards side by side on wide screens and stack them on smaller screens.
- Do not repeat Datasource Select inside `数据来源` or `数据去向`.
- Treat the saved Datasource connection as the database scope authority. The editor must not expose a second Database selector that can override a bound Datasource database.
- Use Datasource Catalog API for Schema / table discovery inside that Datasource scope.
- Show the bound database / schema as read-only scope context under the Datasource Select.
- Show a Schema Select only when the Datasource itself does not bind a default Schema and Catalog exposes Schemas.
- Display backend mapping / Schema Preview as the source of truth.
- Column Mapping Editor is the editable mapping surface: it supports implicit same-name mapping, same-order mapping, explicit field subset / reorder / rename, single-link editing, clear-as-intermediate-state and field search. Existing targets select real Catalog fields; Auto Create with a missing target may define target field names. Frontend must not reimplement JDBC type compatibility or primary-key validation.
- Expose `autoCreateTable` as an explicit Task-definition Switch under `数据去向`; default false and preserve the persisted value when editing.
- The target table stays one field labeled `目标表`: Auto Create disabled renders the Catalog Select only; Auto Create enabled replaces that Select with a controlled Input for the target table name. Never render Select and Input at the same time.
- Switching Auto Create from disabled to enabled preserves the currently selected target table name in the Input. Switching back keeps the name only when that exact table exists in the current Catalog scope; otherwise clear it so the user must choose an existing table.
- Schema Preview must distinguish target exists / missing + disabled / missing + auto-create and consume backend `warnings` / `unsupportedReasons`. Auto Create DDL is auxiliary target-table information: expose the backend `ddlStatements` through the target-table DDL action / Popover, with `createTableSql` only as legacy-response fallback; do not keep a duplicate inline DDL surface in the normal form.
- Warnings are non-blocking; backend `compatible=false` or any blocking unsupported result disables Save / Save & Publish through the same preview compatibility boundary.
- Save does not execute DDL and Runtime re-checks the target before CREATE; existing targets are never ALTERed / DROPed by this feature. This is implementation behavior and must not be rendered as persistent helper copy in the normal form.
- Disable save while the current mapping / Schema Preview is incompatible.
- Expose OFFLINE write mode under `数据去向`, never under runtime tuning. Options are APPEND / OVERWRITE / UPSERT with APPEND as the default.
- Use Yak UI `Alert` when OFFLINE write mode is `OVERWRITE`: warn that the target table is cleared before loading and original data is not automatically restored after a later sync failure. APPEND / UPSERT do not render persistent helper text; backend Catalog validation remains the source of truth.
- Do not expose OFFLINE engine tuning in Task Editor. New ordinary tasks use backend-owned AUTO Runtime Policy; historical / explicit FIXED configs remain executable but are read-only product facts.
- Execution `配置快照` shows the frozen Effective Runtime Config and planning summary (auto/fixed, planned row count when available, split count, read parallelism and batch sizes). Do not recompute these values in frontend or infer them from current Task state.
- Use existing Yak UI primitives.
- Keep the OFFLINE editor as a full-height local-scroll workspace: PageHeader and the desktop section navigator stay outside the scrolling region, while only the definition content column owns vertical scrolling. Do not rely on sticky positioning for these fixed editor controls and do not change the global AppLayout scroll contract for this page.
- Keep the Offline Sync editor definition-focused; runtime history belongs to the separate Task Detail surface, not an editor Tab.
- Task Detail stays inside Data Integration and shows a compact Task summary plus Task-filtered Execution history. The selected OFFLINE Execution uses `执行情况 / 配置快照 / 执行诊断` Tabs: `执行情况` prioritizes result, failure reason and persisted metrics; `配置快照` reads the selected Execution's frozen definitionSnapshot and groups fields into `数据来源 / 数据去向 / 执行策略`; `执行诊断` reads the Runtime Trace Summary plus Source Split / Sink Batch diagnostic APIs. It is read-only for runtime commands.
- OFFLINE Task Detail uses the same full-height local-scroll pattern as the OFFLINE editor: PageHeader stays outside the scrolling region and only the detail content viewport scrolls. Basic Info must not repeat version/type already visible in PageHeader and presents Source → Target as one sync path. Write mode and retry policy are configuration facts and stay out of the Basic Info summary; the selected Execution's frozen values belong to `配置快照`. The primary surface does not expose internal Execution or Datasource IDs. Retry history appears only after an actual retry or while `RETRY_WAITING`. Do not wrap these sections in a second page-wide Card, use sticky positioning, or change the global AppLayout scroll contract.
- `执行诊断` loads only while that Tab is visible. For active OFFLINE Executions, only the Trace Summary polls every 2 seconds; high-volume Source / Sink detail pages do not continuously refetch. Historical Executions may legitimately have no Runtime Trace when they predate the capability or the Trace Store was unavailable, and frontend must show that state instead of synthesizing diagnostics.
- `执行诊断` presents diagnostics in the order Summary → Execution Errors → Source Split → Target Batch → collapsed Lifecycle Events. Source / Sink detail uses backend Cursor pagination with page size 50 and optional SUCCESS / FAILED filtering; frontend must not load the entire JSONL trace into memory or infer throughput time series.
- Runtime Trace rows / durations remain diagnostic facts and never replace the persisted Instance / Attempt readRows / writeRows product metrics. Show the backend `droppedEventCount` warning when Trace detail is incomplete, but do not imply the data sync result is incomplete.
- Lifecycle product events are retained inside `执行诊断` as a collapsed auxiliary section. They continue to poll only while expanded, visible and the selected Execution is active; lifecycle events are audit context, not the main OFFLINE diagnostic surface.
- Operations Center OFFLINE root is an aggregate observability Dashboard. It no longer exposes the previous Task / Instance Tabs or per-Task Run / Stop / Schedule controls; Task Detail remains the place to inspect one Task's Execution history.
- Display readRows / writeRows only from the persisted Instance; frontend must not estimate progress.
- The editor exposes Save and Save & Publish. Save persists an UNPUBLISHED Task; Save & Publish explicitly composes save then publish and returns to the Task list. Runtime execution is not an editor action.
- OFFLINE editor owns optional Schedule definition only: Quartz Cron expression + explicit IANA Time Zone. An empty Cron on a Task that has never created a Schedule means manual-only execution.
- Cron editing uses shared Yak UI `CronSchedulerPicker`: common schedules are configured visually and unsupported advanced Quartz expressions remain available in Advanced Cron mode. The picker may compose a product-owned future-fire preview, but Yak UI itself must not call Data Sync APIs.
- Time Zone is selected rather than free-typed, defaults to `Asia/Shanghai`, and must preserve an already persisted valid zone even when it is outside the common option list. Future 5-fire preview comes from the backend Quartz preview endpoint and is never calculated in browser code.
- Persist Schedule only after Task persistence succeeds because Schedule identity depends on `taskId`; Save & Publish must persist Task, then Schedule, then publish.
- Schedule definition save must never implicitly enable scheduling. A newly created Schedule remains disabled until the user explicitly starts it from the OFFLINE Task list.
- An existing Schedule cannot be removed by clearing Cron in the editor; Cron remains required once the Schedule exists. Runtime enable / disable belongs to the OFFLINE Task list.
- Datasource Select uses `value = datasourceId` and `label = datasourceName`; it must pass the value-label map through `Select.items`.
- Table Select uses a stable composite `tableKey` as value and a human-readable table path as label; it must pass the value-label map through `Select.items`. Target + explicit Auto Create switches the same `目标表` field to a controlled Input that may represent a table not yet present in Catalog.
- Schema Select may omit `items` when the domain value is intentionally identical to the visible label. Database is not editable in Offline Sync when Datasource already binds it.
- Datasource / Schema / Table are dynamic resource Selects: their popup uses Yak UI Select Search composition with local keyword filtering and an explicit refresh action.
- Datasource Select footer exposes “新增数据源” as a product-owned action and routes to Datasource create; Schema / Table do not invent create actions.
- Static enum Selects such as OFFLINE write mode stay simple and do not add search / refresh / footer without a real option-volume need.
- Task list shows the persisted publication status as 已下线 / 已上线 and may filter by that status.
- Task list is a definition summary surface: show task/version, Source → Target, Schedule definition summary, publication status, updater/update time and actions. Sink write mode is not a list-summary field; it belongs to the selected Execution's `配置快照`. Source / Target use the compact `Datasource Name.Table` form and do not repeat bound database/schema context already implied by the Datasource. Render the sync route as a compact vertical flow: Source on the first row, a lightweight downward connector in the icon column, and Target on the second row; both rows use the same neutral Datasource icon rather than database-specific branding. Runtime metrics such as Last Run / Next Run / Attempt / Retry / readRows / writeRows stay in Operations Center or Task Detail.
- Task list Schedule summary renders `Cron: <expression>` plus the persisted enabled state (`已启动 / 未启动`) for scheduled tasks and `手动` when no Cron exists. Time Zone and next-fire stay in editor/detail or Operations observability surfaces. The Task list owns explicit Schedule `启动 / 停止`: starting requires a PUBLISHED Task; stopping does not cancel an already-created Execution.
- Task list updater resolves current-page `updateBy` values with one batch user lookup; never issue one user request per row. Historical `system` stays SYSTEM.
- Task list fixes the action column on the right through Yak UI Table `fixed: "right"`; product code must not rebuild sticky column CSS.
- Task list keeps five stable action slots: 运行、上线/下线、编辑、详情、删除. `详情` is always visible and opens `/offline-sync/:taskId/detail` inside Data Integration.
- `运行` is a manual shortcut for a PUBLISHED Task and creates a MANUAL Execution through the existing Run API. It is disabled while the Task is UNPUBLISHED or already has a PENDING / RUNNING / RETRY_WAITING Execution.
- UNPUBLISHED Task enables 上线 / 编辑 / 详情 / 删除 while 运行 is disabled. PUBLISHED without an active Execution enables 运行 / 下线 / 详情 and disables 编辑 / 删除. An active Execution disables both 运行 and 下线 while 详情 remains available.
- Schedule lifecycle is independent from Task publication but constrained by it: an UNPUBLISHED Task may show a saved Cron as 未启动 but cannot start it; PUBLISHED + configured Cron may start or stop Schedule from the Schedule column. Unpublishing a Task continues to disable its Schedule automatically.
- Stop never executes from the Offline Sync list, editor or Task Detail; users inspect the created Execution from Task Detail after running.
- Direct navigation to an editor for a PUBLISHED Task must not expose an editable form; guide the user back to the list to unpublish first.

Must Not:

- Add configurable scheduler concurrency, misfire or catch-up policies beyond the frozen v1.1 backend contract.
- Add filter SQL, split key, pre/post SQL, resource group or Transform.
- Reimplement JDBC type compatibility in frontend code.
