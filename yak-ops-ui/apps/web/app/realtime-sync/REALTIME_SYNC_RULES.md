# Realtime Sync Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/realtime-sync/**`
- REALTIME mode of `yak-ops-ui/apps/web/app/data-sync/task-editor.tsx`

## Current V1 Boundary

Realtime Sync owns Task definition, publication configuration and Task-scoped read-only runtime detail. Operations Center owns cross-Task REALTIME observability through the aggregate Dashboard. Backend acceptance proves the configured REALTIME path against MySQL, PostgreSQL and Oracle targets; the frontend does not duplicate that database-specific validation logic.

Must:

- Query Task list with `syncType = REALTIME`.
- Source Datasource must be MySQL.
- Target Datasource may be MySQL / PostgreSQL / Oracle.
- Reuse the shared Data Sync Task Editor for Catalog and field mapping.
- Treat backend validation as the source of truth for exact Source/Target primary-key correspondence; do not reimplement the PK contract in frontend state.
- Do not expose REALTIME engine tuning or Retry Policy in Task Editor. Ordinary save requests omit `realtimeConfig` / `retryPolicy`; backend-owned defaults and SMART Retry are product policy, while persisted Task / Execution snapshots remain observable facts.
- Historical or explicit REALTIME configs remain executable and may be displayed read-only in detail surfaces; hiding tuning from the editor must not overwrite existing values with current defaults.
- Explain that first start performs the initial snapshot and then continuously consumes MySQL Binlog.
- When editing an existing UNPUBLISHED REALTIME Task, show a Yak UI `Alert` that executable-definition changes create a new Task version and that version's first start performs a fresh initial snapshot; metadata-only name/remark changes do not increment the version.
- In the REALTIME source section, show a Yak UI `Alert` that ROW Binlog and CDC account permissions are required; ordinary Datasource connection-test success does not prove CDC readiness.
- The editor exposes Save and Save & Publish. Save persists an UNPUBLISHED Task; Save & Publish explicitly composes save then publish and returns to the Task list. Runtime start is not an editor action.
- Task list shows the persisted publication status as 已下线 / 已上线 and may filter by that status.
- Task list is a definition summary surface: show task/version, compact Source → Target, publication status, updater/update time and actions. Do not repeat the product-level `MySQL CDC` mode in every row.
- Source / Target use the compact `Datasource Name.Table` form and the same vertical flow used by OFFLINE: Source first, lightweight downward connector, Target second. Do not repeat database/schema context in the list summary.
- Resolve current-page `updateBy` values with one batch user lookup and render updater + update time as `更新信息`; historical `system` stays `SYSTEM`.
- Fix the action column on the right through Yak UI Table `fixed: "right"`; do not rebuild sticky-column CSS in product code.
- Task list keeps five stable action slots: 启动/停止/重新启动、上线/下线、编辑、详情、删除. `详情` is always visible and opens `/realtime-sync/:taskId/detail` inside Data Integration.
- The runtime action follows persisted Task intent plus active Execution state: UNPUBLISHED has disabled 启动; PUBLISHED + no active Execution + desiredState=STOPPED shows 启动; active PENDING / RUNNING / RETRY_WAITING shows 停止; PUBLISHED + no active Execution + desiredState=RUNNING shows 重新启动.
- 启动 / 重新启动 uses the existing Task Run API and creates a MANUAL Execution; 停止 cancels the active Execution and therefore changes REALTIME desiredState to STOPPED through the backend contract. The list must refresh both Task facts and active Execution state after either command.
- UNPUBLISHED Task enables 上线 / 编辑 / 详情 / 删除 while 启动 is disabled. PUBLISHED without an active Execution disables 编辑 / 删除. An active Execution keeps 停止 / 详情 available and disables 下线 / 编辑 / 删除.
- Runtime Start / Stop never execute from the Realtime Sync editor or Task Detail; the Task list owns these shortcuts while Task Detail remains read-only.
- Direct navigation to an editor for a PUBLISHED Task must not expose an editable form; guide the user back to the list to unpublish first.
- Keep the Realtime Sync editor definition-focused; runtime history belongs to the separate Task Detail surface, not an editor Tab.
- Task Detail uses the same full-height local-scroll master-detail layout as OFFLINE: the left Execution list stays fixed, the right PageHeader stays outside the scrolling region, and only the detail content viewport scrolls. Basic Info keeps only task status, desired state, Source → Target and update/remark context; do not repeat the product-level `MySQL CDC · 首次全量后持续消费 Binlog` mode there. The selected Execution uses `执行情况 / 配置快照 / 执行日志` Tabs: `执行情况` uses the shared result-first presentation, keeps internal Execution ID out of the primary surface, places failure context before metrics and only expands retry history when retries exist; `配置快照` reads the selected Execution's frozen definitionSnapshot and groups Source-side CDC parameters, Sink-side write parameters and Execution strategy such as Checkpoint / timeout / retry; logs display persisted product events, not Server Log lines.
- The left REALTIME Execution list additionally shows the persisted Trigger Type as secondary context (for example 手动运行 / 自动恢复), so LOST → AUTO_RECOVERY transitions are understandable without opening each record. Selected Execution status continues polling only while active data exists. `执行日志` polls every 2 seconds only while the log Tab is visible and the selected Execution is PENDING / RUNNING / RETRY_WAITING; terminal or hidden logs do not keep polling. Historical Executions may legitimately have an empty event timeline and frontend must not synthesize missing history.
- Operations Center REALTIME root reads the aggregate Dashboard with `syncType = REALTIME`; never fetch mixed OFFLINE/REALTIME Instance pages and filter them only in frontend memory.
- The REALTIME Dashboard is observability-only and shows current active Task count, abnormal Task count, AUTO_RECOVERY count, abnormal Execution count, Execution creation trend, status distribution, automatic recovery trend and FAILED + LOST Task ranking.
- The Dashboard must not present readRows / writeRows as TPS, events/s, CDC Lag or Checkpoint Lag. Those continuous runtime metrics require a future persisted Metrics Time Series.
- Legacy Operations Execution detail routes remain compatibility-only. If directly opened, active REALTIME detail may still show Stop plus the Yak UI `Alert` about resuming from the latest completed Checkpoint; the Dashboard does not link to those routes as its primary workflow. Task Detail remains read-only.
- Display persisted `readRows/writeRows` as `读取事件/写入事件`; do not rename them to business row counts.
- Explain that UPDATE produces UPDATE_BEFORE + UPDATE_AFTER events in current YakFlow metrics.
- Display REALTIME snapshot runtime parameters from `realtimeConfig`.
- Display CANCELED as `已停止` for realtime product wording.
- Datasource / Schema / Table are dynamic resource Selects: their popup uses Yak UI Select Search composition with local keyword filtering and an explicit refresh action.
- Datasource Select footer exposes “新增数据源” as a product-owned action and routes to Datasource create; source/target type restrictions still come from Realtime product rules.
- Static lifecycle/status Selects stay simple and do not add search / refresh / footer without a real option-volume need.

Must Not:

- Show or edit state directories, Debezium offsets, schema history or serverId.
- Invent or estimate checkpoint time when the backend does not persist it.
- Add scheduler controls, user-configurable runtime / retry tuning, Transform, DDL sync or multi-table configuration.
- Copy the Offline Instance implementation; OFFLINE and REALTIME wrappers must reuse the shared Data Sync runtime component.
