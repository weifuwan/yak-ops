# YOIP-21: Data Integration Workflows Aligned with DataWorks

Status: Discussion

Discussion: Pending proposal PR creation

Decision: Pending maintainer review

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

The Data Sync application exposes connector implementation, table scope, and execution mode as peer choices. For MySQL to MySQL, the creation dialog expands JDBC and CDC capabilities into six choices: single-table offline, database offline, single-table realtime, database realtime, and single-table/database CDC snapshot variants. Users must choose an implementation before expressing what they want to do.

The desired product has two primary guided workflows:

- **Single-table offline** prepares one table for a target, with field mapping, projection expressions, row filtering, and manual or scheduled bounded execution.
- **Database realtime** copies selected tables, including a selection of one table. Initial full load, incremental-only operation, and full-only completion are strategies within this workflow rather than additional product types.

The application must provide distinct editing experiences for table-level data preparation and database replication. The implementation already provides the underlying JDBC, CDC, snapshot, transform, and execution capabilities. This proposal changes how the application assembles, edits, queries, and presents those capabilities.

DataWorks provides the relevant product precedents:

- [Supported data sources and synchronization solutions](https://help.aliyun.com/zh/dataworks/user-guide/supported-data-source-types-and-read-and-write-operations/) distinguishes table-level preparation from bulk replication and describes the constraints of field-based offline increments.
- [Single-table realtime operations](https://help.aliyun.com/zh/dataworks/user-guide/maintenance-for-real-time-synchronization-nodes) recommends using a database realtime task with one selected table when the channel supports it.
- [CreateDIJob](https://help.aliyun.com/zh/dataworks/developer-reference/api-dataworks-public-2024-05-18-createdijob) documents full-only, incremental-only, and full-plus-incremental strategies for `DatabaseRealtimeMigration`.
- [Database offline configuration](https://help.aliyun.com/zh/dataworks/user-guide/configure-a-database-wide-offline-synchronization-task) retains periodic field-based extraction as a separate capability.

These references establish interaction patterns, not a claim that DataWorks has only two task types or that every DataWorks channel is available in Yak Ops. The scope is the Yak Ops Data Sync application and its application services. Alignment does not add DataWorks resource groups, distributed execution, dynamic table discovery, or unsupported transformation operators.

This proposal refines the product interaction model in [YOIP-5](YOIP-5-data-integration-sync-product-model.md). It does not reinstate the historical V3 or V4 parameter formats. The current [schema version 7 Pipeline contract](../data-sync-pipeline.md) remains authoritative.

## Public Interfaces

### Product workflows and application projection

The new-task dialog retains the existing concrete-type field and offers the two primary workflows supported by the selected source/sink channel. Separate single-table realtime and CDC snapshot choices are removed from guided creation. Database realtime retains its localized product name even when its selected strategy is full-only; the strategy and actual execution behavior are always presented separately.

An application-owned projection distinguishes the following concepts:

| Concern | Proposed values or meaning | Owner |
| --- | --- | --- |
| Product profile | `SINGLE_TABLE_BATCH`, `DATABASE_REPLICATION`, `MULTI_TABLE_BATCH` | Data Sync application |
| Execution mode | Existing `BATCH` or `STREAMING` | Validated Pipeline definition |
| Effective table scope | Existing explicit `SINGLE` or `DATABASE`, with the existing default when omitted | Validated Pipeline definition |
| Read strategy | Existing JDBC sync mode or complete CDC startup configuration | Selected source capability |
| Scheduling eligibility | Existing channel and bounded-execution constraints | Task application and plugin |
| Editing coverage | Standard guided fields and preserved advanced rules | Data Sync editor |

`SINGLE_TABLE_BATCH` is displayed as single-table offline. `DATABASE_REPLICATION` is displayed as database realtime, including its full-only strategy. `MULTI_TABLE_BATCH` keeps the database-offline identity for supported existing definitions and API consumers. It is not an additional primary guided-creation choice in this proposal.

For valid schema version 7 definitions, the product profile is derived deterministically:

1. A definition with `source.startup`, or with `execution.runtime-mode = STREAMING`, belongs to `DATABASE_REPLICATION`.
2. Otherwise, use the explicit `pipeline.table-scope` when present. Only when it is absent, retain the existing default of one source table meaning `SINGLE` and more than one meaning `DATABASE`.
3. A non-replication definition with effective `SINGLE` scope belongs to `SINGLE_TABLE_BATCH`; one with effective `DATABASE` scope belongs to `MULTI_TABLE_BATCH`.

This projection describes the effective definition, not historical telemetry about which creation control a user clicked. It is not stored in Pipeline JSON, YAML, checkpoints, or a new database column. Invalid or unsupported parameter versions retain their existing unreadable-configuration presentation; the application does not guess a runnable profile for them.

The installed connector catalog controls which new workflows and strategies are available. It must not decide whether a historical definition appears in a product-filtered list: removing a connector must leave its definitions visible with an actionable capability error. Connector name prefixes are not a classification protocol.

### Query and response changes

Extend the existing Task Definition paging query with the optional application filter `parameterFilters.dataSyncProfile`, restricted to `DATA_SYNC` and the three profile values above. The application service validates the value and compiles it to fixed, parameter-bound repository predicates over the existing versioned parameters and trusted indexes. Filtering and counting happen before pagination.

The existing six exact filters keep their meanings and combine with the new filter using `AND`. In particular, `runtimeMode = BATCH` must continue to include a bounded CDC snapshot. There is no new client-controlled query index and no historical definition rewrite or backfill.

New list URLs use a distinct product-profile parameter. Existing `solutionType` URLs continue to mean their original runtime/scope selection during the documented transition; they are not silently reinterpreted as the new product profile. A legacy-filter indicator with an explicit clear action can retain that meaning without showing the old choices in the new-task dialog. If both URL forms are present, apply the product and legacy execution constraints together, matching the server contract.

Full validated definition JSON remains the read authority. The frontend service layer has one pure product-projection implementation consumed by creation restoration, lists, details, and historical execution views. Server-side query predicates and this projection are verified against the same fixture set. Pages must not invent additional inference rules. An additional persisted product discriminator or duplicate task model is unnecessary.

### Preserved interfaces and validation boundaries

The following contracts remain unchanged:

- Parameter schema version 7; source/sink factory identifiers; `source`, `sink`, `route`, `transform`, and `pipeline` data shapes.
- Task identity, immutable definition versions, publication, submission, Instance lifecycle, and authorization boundaries.
- JDBC/CDC execution, field cursors, source/sink contracts, transform processors, checkpoint formats, recovery, and commit guarantees.
- Credential handling, Workspace isolation, source metadata authorization, existing schedule revisions, and partial-write recovery.

The field editor emits existing transform rules. It introduces no field-mapping execution protocol and no transformation dependency. Save-time structure/reference checks and publication-time resource/schema validation remain distinct. This PR does not introduce sampled-data preview, execute arbitrary SQL for preview, or present a save result as successful expression execution.

## Proposed Changes

### Application architecture and scope

The application owns product intent, interaction state, capability selection, lossless parameter assembly, and display. Existing lower layers continue to validate and execute the resulting definition.

Production changes are limited to the Data Sync frontend domain and services, and the minimal Task application/query adaptation needed for product-profile filtering. Existing source/target metadata APIs, Task save/publish APIs, execution actions, and schedule APIs are reused. Generic Yak UI controls are extended only when a new reusable control capability has a real consumer.

The refactor must replace the current product-choice expansion as the creation authority. It must not keep two competing creation models, duplicate full editors, or a second execution path. The full underlying capability set remains available to read and edit supported definitions, even when guided creation exposes only two profiles.

### Creation and navigation

1. Select source and target datasource types, concrete product type, and task name. Available types come from installed connector-pair capabilities.
2. Enter the corresponding editor and select authorized concrete connections. Metadata loads are scoped to the user, Workspace, connection, and selected table.
3. Configure either a single table's data preparation or the selected replication tables and strategy.
4. Save a draft definition, configure a schedule when eligible, and publish through the existing submission lifecycle. The UI distinguishes a saved definition, a configured schedule, successful publication, and an executing Instance.

Use the existing Yak UI controls, compact form geometry, theme tokens, keyboard behavior, and error surfaces. Do not introduce a general canvas/DAG editor or clone unrelated DataWorks application chrome. Persistent explanatory text is limited to meaningful prerequisites, data omission, destructive structure changes, and blocked actions.

Changing a connection, product family, or source table must identify affected mappings and expressions before clearing them. Existing task updates continue to respect the current prohibition on changing source/sink factory identity. A display-only product reclassification is never treated as a factory change.

### Single-table offline preparation

The editor organizes configuration around source table, field mapping and expressions, target table, and execution scheduling.

The first implementation includes:

- Source/target field metadata, explicit column selection and ordering, direct same-name mappings, and aliases to target field names.
- Projection-expression text and a separate row-filter expression using the existing transform language.
- Existing full, incremental, and full-then-incremental JDBC strategies where the selected connector supports them.
- Existing target write modes and initial target-schema policies, with capability-dependent validation.

Aliases are represented by existing projection output names, not a parallel mapping field in the persisted definition. A complete direct alias of a physical key may require explicit existing transform `primary-keys`; the UI must not invent uniqueness, infer computed keys, discard part of a composite key, or change a write mode merely because a field was renamed.

The visual mapping representation is restricted to rules that can be represented and reconstructed exactly. Arbitrary expressions, multiple ordered rules, complex selectors, and supported options outside that representation remain visible in an advanced expression/rule view within the same editor and submission flow. Original rules are the serialization baseline: only a rule deliberately edited by the user is replaced. Never use comma splitting or approximate SQL reconstruction to load existing expressions.

Direct mappings can use a structured field table. Free-form expressions remain expression text unless a lossless structural representation is available. Switching representations must preserve the exact rule or require an explicit replacement action. Do not add an independent SQL parser, expression evaluator, output-type inference engine, or generic script runtime in the browser.

No-edit round trips retain `*`, omitted projections, rule order, selector text, aliases, filters, declared keys, descriptions, time zone, and decimal settings. In particular, opening the field table must not silently expand `*` into the columns present at that moment. Explicitly choosing a fixed column selection is a user-authored parameter change.

The existing processor has executable coverage for projection, aliases, arithmetic, selected functions, and filtering. A broad function catalog or arbitrary type-conversion dropdown is not part of the initial UI. Existing `CAST` expressions remain intact; any future generated conversion must have value, output-schema, null, and error behavior verified against the real processor before the UI offers it as a supported operation.

Field metadata and obvious mapping errors may be shown immediately. Full output-type, nullability, key, and sink-schema validation continues through the existing publication/admission validators. The UI must not label metadata-derived guesses as validated transform output. Invalid expressions may remain in a saved draft when the existing save contract allows them, but publication failure must remain explicit and must not mark the task as published.

Changes to projection, filter, transformed keys, or expression settings retain the existing parameter-version and incremental-cursor reinitialization behavior. They are not cosmetic edits.

### Database realtime replication

Use one table-selection and table-mapping workflow for one or multiple tables. The current explicit selection limit and one-to-one route constraints remain. Selecting the current set of all tables does not subscribe to future tables or enable dynamic table discovery.

The user chooses one of three data strategies:

| Strategy | New-definition mapping using existing capabilities | Observable behavior |
| --- | --- | --- |
| Initial full load and continuous changes | CDC `INITIAL`, `STREAMING` | Initialize selected data, then continue reading changes |
| Changes only | CDC `LATEST_OFFSET`, `STREAMING`, unless another supported start position is explicitly configured | Read changes without backfilling existing rows |
| Full load only | CDC `SNAPSHOT`, `BATCH` | Copy existing data and finish |

Initial full load plus changes is the default for a new standard replication task. Incremental-only explicitly warns that existing rows are not copied. A full-only task remains in the database-realtime product family after save, reload, filtering, and reopening. It does not acquire a separate CDC-snapshot product name.

Target preparation, existing-table verification, table-name mapping, and continuous schema evolution are distinct controls. Initial `CREATE_OR_ALTER` is not described as create-only and is not silently enabled. Column deletion remains an explicit existing capability. Continuous evolution is unavailable for a full-only run.

Standard new replication tasks do not author business transform rules. Existing replication tasks with transforms expose their retained rules through the advanced rule view; selecting the replication workflow must never clear `transform`.

Ordinary replication uses the existing supported keyed-write behavior. Existing single-table keyless CDC and `RETRACT`/`CHECKPOINT_XA` remain supported. When creating that supported case, expose the row-identity/write choice only after the selected single table requires it, and obtain explicit configuration before selecting the internal `SINGLE` scope. Adding another table to a single-table-only configuration is blocked until the conflicting setting is resolved. The UI does not silently convert it to `DATABASE`, `UPSERT`, or another transaction mode.

Capability errors remain errors. A source with no suitable CDC pair cannot create the replication workflow; a connection that fails CDC prerequisites is not automatically replaced by JDBC. Type-level discovery does not claim that the specific connection's Binlog or permissions have passed resource validation.

### Lists, details, execution history, and scheduling

Lists and task details show the product profile separately from the selected strategy and table count. A replication full-only task must be findable under replication while retaining its real bounded-execution behavior.

Scheduling, run/start/stop actions, active-instance guards, progress, and terminal-state interpretation use actual execution capabilities. A product name containing realtime is not a reason to disable a valid existing snapshot schedule or to interpret normal snapshot completion as a failure. Continuous CDC does not gain a periodic-launch schedule.

Execution views project the immutable definition version bound to the selected Instance. They never substitute the latest task's tables, strategies, transforms, or product projection for historical execution facts. Existing execution-report formats, stage counters, checkpoint information, and logs are preserved.

Existing operations statistics using `OFFLINE`/`REALTIME` as `BATCH`/`STREAMING` retain that meaning. Product labels must not silently change historical metric denominators. A separate product-profile dashboard dimension is outside this implementation's required scope.

### Drafts, failures, and ownership

Draft state remains tab-local and scoped to user and Workspace. Derived product labels are not a second persisted authority. If the editor's draft representation changes, migrate supported version 2 session drafts deterministically, preserving connector identity, steps, table mappings, transforms, and startup parameters. Unrecoverable drafts require an explicit discard/recreate choice; they must not be overwritten by a blank draft. The draft decoder is a bounded transition for active version 2 sessions, not an alternate task-definition execution format; its support and retirement condition must be recorded with the implementation release.

Keep metadata dirty state separate from parameter dirty state. Renaming a task or changing its description does not rewrite its Pipeline parameters. Changing schedule-only settings follows the existing schedule API and revision semantics.

Retain the existing save sequence and partial-success recovery. If task creation succeeds and schedule save fails or has an unknown outcome, retain the created task ID and require the appropriate reload/retry; do not submit another task creation. If publication fails, show the task as saved but unpublished. Workspace switching cancels or isolates outstanding metadata and task requests.

## Compatibility, Deprecation, and Migration Plan

### Existing definitions and clients

No database migration, parameter-schema upgrade, checkpoint conversion, or bulk task rewrite is required. Read and display operations do not create new definition versions. Existing schema versions that the application already rejects remain rejected rather than being silently migrated.

| Existing configuration | Required behavior after the UI refactor |
| --- | --- |
| JDBC single-table full/incremental/full-then-incremental | Display as single-table offline; retain every incremental, mapping, write, and transform setting |
| JDBC explicit database scope with one or multiple tables | Retain database-offline identity, multi-table editing, filtering, scheduling, publication, and execution; never truncate to one table |
| CDC snapshot with either scope | Display in the replication family with full-only strategy; retain bounded execution and any supported schedule |
| CDC `INITIAL`, including a valid omitted startup | Display as replication without resetting initialization semantics |
| CDC `EARLIEST_OFFSET`, `LATEST_OFFSET`, `SPECIFIC_OFFSETS`, or `TIMESTAMP` | Preserve the complete original startup configuration, including file/position, GTID, skip, and timestamp values |
| Single-table `RETRACT` with XA | Preserve `SINGLE`, write identity, transaction settings, snapshot chunk key, and existing schema restrictions |
| Replication with existing transforms | Remain visible under replication and retain all rules through advanced editing |
| Supported Pipeline options not represented by normal controls | Preserve the original values when unrelated fields change |
| Unreadable definition or unavailable connector | Keep task identity and a clear error visible; do not fabricate a replacement configuration |

The existing multi-table JDBC capability remains valid in the Task API and in the common editor. It is not relabeled as replication, converted into multiple single-table tasks, or removed from the application. The two primary creation workflows are a product simplification, not a change to the accepted execution contract. Adding a promoted guided entry for multi-table periodic extraction later requires a demonstrated product need, not another connector Cartesian product.

Existing immutable Task versions and Instances keep their parameters and IDs. Old runtime/scope filters preserve their semantics through the documented URL transition. New product filters are additive and never implemented by filtering a single fetched page on the client.

### Delivery and acceptance

This proposal must be accepted through the repository's recorded decision process before the implementation is merged. The implementation is planned as one coherent application refactor with reviewable commits: projection/query contract; shared editing and lossless restoration; two creation workflows; single-table field preparation; list/detail/history and lifecycle integration; focused verification and user documentation.

Do not ship only the reduced creation menu while field editing, full-only restoration, or existing-definition editing is incomplete. Use one authoritative editor and one existing execution pipeline. Test-only fixtures may call the unchanged engine and connectors to verify application-generated definitions; this does not authorize production changes to lower-layer behavior.

Update the Data Sync user guide and applicable release notes to explain the two primary workflows, full-only replication, field preparation, existing database-offline tasks, and configuration compatibility. Preserve accepted proposal history rather than rewriting YOIP-5 or YOIP-20 as current implementation documentation.

## Test Plan

Implementation verification must distinguish static checks, browser interaction, application API/repository behavior, real transform processing, and real connector execution. This proposal itself does not claim those implementation checks have run.

### Shared projection and query fixtures

Use the same valid schema version 7 fixture matrix to verify frontend projection, server-side filtering/counting, and read/edit/write preservation:

| Fixture | Required observation |
| --- | --- |
| JDBC single full, incremental, and full-then-incremental | Single-table product profile, BATCH behavior, intact cursor settings |
| JDBC explicit database scope with one and two tables | Database-offline profile in both cases; scope is not inferred over an explicit value |
| CDC snapshot with single/database scope | Replication profile plus BATCH behavior, including valid scheduling eligibility |
| CDC initial and streaming with omitted startup | Replication profile and correct initialization semantics |
| CDC every supported incremental-only start position | Exact startup round trip |
| CDC single-table RETRACT/XA | Original scope, write, transaction, and key restrictions retained |
| CDC existing complex/multiple transform rules | Present in replication results and lossless under unrelated edits |
| Unreadable or unsupported definition version | Visible task identity and explicit error in unfiltered views |

Verify exact row membership, totals, stable pagination, combined legacy/profile predicates, and Workspace isolation on the supported repository test database. A profile query must include snapshots and advanced replication definitions even if their connector is currently unavailable. Reject unknown product-filter values without interpolating query paths or SQL.

### Field preparation and round trips

Verify direct mappings, aliases, explicit column ordering, removed columns, expression text, filters, and the resulting existing Pipeline JSON. Reopen saved tasks and restored drafts without changing their rules.

Use the existing processor and composer to verify representative UI-generated projections and filters against actual input/output rows and output schema. Cover nested function/string commas, identifier quoting, duplicate output aliases, missing target columns, nullability, renamed keys, incomplete composite keys, and invalid computed keys. Do not treat parser acceptance as proof of value conversion.

Unrelated edits must preserve wildcard projections, empty/default projections, rule order, selectors, filters, keys, descriptions, supported extra options, time zone, precision settings, startup positions, and cursor bindings. Semantic edits must retain existing reinitialization requirements. Metadata-only edits must not create a new parameter version.

### Browser and lifecycle scenarios

- MySQL to MySQL exposes two primary concrete types and no separate CDC snapshot or single-table realtime creation option.
- Unsupported channels and capability-load failures cannot proceed using invented defaults.
- Single-table offline provides working field mapping and expression/filter editing, not only a renamed table-mapping component.
- Replication supports one or multiple tables and all three data strategies. Full-only save/reload/filter/detail/history remains replication and completes as bounded work.
- Existing multi-table JDBC, keyless CDC, advanced transforms, and startup positions remain usable without destructive conversion.
- Field changes, connection changes, browser refresh, navigation away, user changes, and Workspace changes preserve or explicitly resolve the correct draft.
- Task-save success followed by schedule failure/unknown outcome or publish failure does not create duplicates or claim success for the failed step.
- Historical execution views use their bound definition version after the current task has been edited.
- Keyboard navigation, focus, validation placement, responsive layout, light/dark themes, and reduced-motion behavior follow existing Yak UI conventions.

Extend the existing Data Sync definition/editor/list/detail contract checks and production-preview Playwright scenarios. Preserve existing round-trip, schedule-recovery, and scope-isolation coverage. Run `npm run check` and `npm run build`; browser checks are a separate required evidence item for this refactor.

Run focused application/query tests for changed backend services and SQL predicates. Exercise representative generated definitions with existing real JDBC/CDC suites where the application mapping changes, including single-table projection/filter and full-only replication. UI mocks establish interaction and payload behavior only. The implementation PR must report exact tested commits, selected suites, real results, and anything not run. Broad checkpoint/recovery reruns are required only if a concrete changed contract reaches them; this proposal does not change that contract.

## Rejected Alternatives

### Keep all connector, scope, and runtime combinations as product types

This exposes implementation choices at the point where users should select a preparation or replication workflow. Future connectors would multiply choices without adding a new user goal.

### Replace the two workflows with only batch and realtime selection

This loses the agreed distinction between single-table field preparation and database replication. A full-only replication run would again be classified by execution mode rather than its product workflow.

### Hide four creation choices and keep the current inferred type everywhere else

Current runtime/scope inference would reclassify CDC snapshots after save, omit them from replication filters, and leave historical views inconsistent. The application projection and lifecycle consumers must change together.

### Force every replication definition to database scope or continuous execution

This breaks full-only runs, valid bounded schedules, and the existing single-table RETRACT/XA path. Product grouping does not override existing execution constraints.

### Add a new persisted product tag or parameter version

The current validated JSON already determines the required profile. A second persisted discriminator would require migration and consistency rules without a present consumer for historical creation-entry telemetry.

### Remove or silently reinterpret multi-table JDBC and advanced definitions

Periodic field extraction is not equivalent to CDC. Existing definitions, scripts using schema version 7, and advanced rules are supported consumers. They retain the existing execution and common-editor capabilities; no alternate legacy engine is introduced.

### Build a new transform, preview, or distributed execution engine

The requested change is in interaction and application capabilities. Reusing existing projection/filter processors and admission keeps execution guarantees and dependency boundaries intact. Data preview, arbitrary SQL, UDF/model authoring, dynamic table discovery, multi-target routing, and distributed deployment are outside this proposal.
