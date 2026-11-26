# Data Modeling E2E Regression Follow-up

This plan records the four implementation stages for the modeling E2E findings. It deliberately separates code defects, environment prerequisites, and capabilities that are not implemented yet.

## Stage 1 — P0: unblock the existing modeling flow

### Code changes
- Add an idempotent schema patch for `t_yak_ops_dimension_table_publish.table_model`. Fresh-install schema already contains the column; existing installations must run the matching database patch.
- Query Doris field types when a physical model type is selected, including before the first save. Keep the local field-type list as a fallback while the API is loading or unavailable.
- Load indicator form option resources independently so one failed lookup does not blank every dropdown.
- Keep modifier owner entry usable when the admin-only user-list endpoint is unavailable, and explain the manual-entry fallback.
- Provide a direct route to Data Source Management when no Doris source is configured.

### Environment prerequisite
- Configure a real Doris data source in Data Source Management. The E2E suite must not treat MySQL as a Doris substitute.
- Do not store connection credentials in source control or E2E fixtures.
- After the source is configured, verify DDL preview, incremental publish, drop-and-recreate, publish history, and physical-schema verification.

### Acceptance
- Existing publish-history tables can be upgraded without recreating or deleting records.
- A new model can add a field and select a type before its first save.
- An unavailable user-list endpoint does not block manual owner entry.
- A failed optional option lookup does not erase successful option resources.
- Publish remains restricted to Doris data sources.

## Stage 2 — P1: align state and lifecycle semantics

- Isolate visible indicator tabs by the selected indicator kind while retaining drafts in other kinds.
- Return `currentVersion` from the shared versioned-asset detail service so the editor header and version history agree.
- When composite-indicator business category changes, retain the selected business process if it remains valid; clear it only after the refreshed cascade proves it is unavailable.
- Rename the overview list to **Latest Submitted** / **最新提交**. A modeling submit creates a version snapshot; it is not proof that a physical Doris table was published.

### Acceptance
- Switching indicator types never renders tabs belonging to another type, and switching back restores the earlier tabs.
- The header version equals the newest version-history record after submit and rollback.
- A compatible business process survives category changes.
- The overview does not describe submitted assets as physically published assets.

## Stage 3 — P2: naming, duplicate data, and misleading actions

- Standardize business-category abbreviations to lowercase letters, digits, and underscores, with no leading or trailing underscore. Normalize and validate on both client and server.
- Deduplicate legacy time-period category rows in list responses and option rendering; reject duplicate category codes within the project.
- Remove the inactive “View data details” action until a real destination is implemented.

### Acceptance
- Uppercase input is normalized to lowercase and duplicate abbreviations are rejected case-insensitively.
- Legacy duplicate category rows do not create duplicate selectable grains.
- Root directory nodes are not selectable as a time-period grain.
- The overview contains no inert detail action.

## Stage 4 — planned capabilities (not part of the current bug-fix implementation)

The following controls remain disabled intentionally. They must not be enabled until their end-to-end behavior and permissions are implemented.

| Capability | Planned scope | Minimum acceptance |
|---|---|---|
| Model logs | Query model publish and related execution logs; link each entry to its model/version/publish record | Correct project and model scoping, readable status/error details, retry-safe pagination, permission checks |
| Quality rules | Define reusable field/table rules, bind rules to models, execute and retain results | Rule lifecycle, scheduling/execution, result history, severity and failure details |
| Model development | Generate and manage model SQL/development tasks without coupling modeling to workflow internals | Version-aware SQL, dependency metadata, explicit execution entry point, observable task state |
| Export | Export model metadata, field definitions, DDL, and selected version | Stable schema, UTF-8 output, correct version selection, no silent truncation |
| Reverse modeling | Select a data source/schema/table, preview physical metadata, map source types, generate a draft model, validate before save | Read-only preview before generation, explicit type mapping, duplicate-name handling, generated draft remains editable, no automatic publish |

### Suggested delivery order
1. Implement model logs and export as read-only capabilities first.
2. Design quality-rule data structures and execution lifecycle separately from the model editor.
3. Design model-development execution as an independent capability; do not change workflow core semantics.
4. Implement reverse modeling as a preview-and-generate workflow. Reuse existing datasource metadata and type-mapping rules where possible.

## Regression commands

Frontend:
- `npm run test:modeling-e2e-regression`
- `npm run test:composite-indicator`
- `npm run test:modeling-overview`

Backend:
- `mvn -pl yak-ops-modeling -am test`

For a complete physical publish E2E, configure a disposable Doris database and run against that database. Never run destructive publish scenarios against shared or production data.
