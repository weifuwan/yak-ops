# YOIP-22: Per-Event DDL Strategies for Data Sync

Status: Accepted

Discussion: https://github.com/weifuwan/yak-ops/pull/1828

Decision: [VOTE] Accepted by maintainer: https://github.com/weifuwan/yak-ops/pull/1828#issuecomment-6552219949

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

Yak Ops currently exposes the two switches "schema evolution enabled" and "allow drop columns" for continuous Data Sync. The switches cannot express the eight independently configurable DDL categories shown in the DataWorks task editor: create table, drop table, add column, drop column, rename table, rename column, modify column type, and truncate table. They also conflate the source's ability to **observe** a DDL event with the sink's permission to **execute** it. A task cannot currently choose to ignore one event type while failing on another.

The first supported Yak Ops path is MySQL CDC to MySQL or PostgreSQL JDBC. This path already coordinates safe nullable add-column, safe same-family column widening, and explicitly authorized non-key column drops. It does not currently support a complete runtime pipeline for TRUNCATE, rename, or DROP TABLE. Some existing metadata event types and dialect SQL helpers are not equivalent to implemented end-to-end behavior.

DataWorks provides the product reference, but not a transferable runtime contract: its per-type strategies are conditional on source and destination capabilities. The [DataWorks DDL strategy guide](https://help.aliyun.com/zh/dataworks/user-guide/management-and-configuration) describes normal handling, ignore, and error; its [source/channel FAQ](https://www.alibabacloud.com/help/zh/dataworks/real-time-synchronization-1) documents connector-specific differences. The [Debezium MySQL event contract](https://debezium.io/documentation/reference/stable/connectors/mysql.html) reports table changes as structured metadata and TRUNCATE as an `op=t` change event. Merely adding Select controls or passing arbitrary SQL to JDBC would not implement these behaviors safely.

This proposal is separate from YOIP-21, which covers Data Sync product workflows and did not approve a new DDL policy, target DDL execution, or a checkpoint compatibility change. A proposal is not implementation or acceptance evidence.

## Public Interfaces

### Persisted Task parameter schema

Introduce parameter schema version **9**, while continuing to read supported versions 7 and 8 with their original semantics. Version 9 replaces `sink.schemaPolicy.evolution` with an explicit, required `sink.schemaPolicy.ddl` object for continuous CDC tasks:

```json
{
  "sink": {
    "schemaPolicy": {
      "initial": "CREATE_OR_ALTER",
      "ddl": {
        "createTable": "IGNORE",
        "dropTable": "IGNORE",
        "addColumn": "NORMAL",
        "dropColumn": "IGNORE",
        "renameTable": "IGNORE",
        "renameColumn": "IGNORE",
        "modifyColumnType": "IGNORE",
        "truncateTable": "NORMAL"
      }
    }
  }
}
```

Allowed policy values are `NORMAL`, `IGNORE`, and `ERROR`, but not every event type or connector pair exposes every value. All eight keys must be present for a continuous v9 task; duplicates, unknown keys and unknown values are rejected. A bounded task omits `ddl` altogether and retains `initial`.

The externally defined meaning of each value is:

- **NORMAL**: apply only the source operation for which the selected source/target pair advertises a tested implementation. Structural safety, physical schema, key, privilege, and native-type checks remain mandatory. Failure stops the task; NORMAL is not unrestricted DDL execution.
- **IGNORE**: do not apply the corresponding target DDL. Advance source metadata, stream position, route ownership and row projection only when safe, checkpoint them coherently, and emit a structured task event explaining the ignored operation. If later rows cannot be represented safely against the unchanged target, fail rather than truncate, coerce or silently discard data.
- **ERROR**: classify and fail the task before applying any target operation for the DDL event. Preserve the source offset for deterministic recovery and an actionable error message.
- Unclassified, ambiguous, partially observed, or untrusted events always fail closed. IGNORE is **not** a blanket exception handler.

This proposal does not add DataWorks' separate optional `WARNING` strategy or a global per-destination default policy. Those require independent product and alerting decisions.

### Pipeline YAML and server capability contract

The Task plugin maps the v9 object to a fixed, validated set of sink-owned `yak.schema.ddl.<event-name>` options, with event-name from `create-table`, `drop-table`, `add-column`, `drop-column`, `rename-table`, `rename-column`, `modify-column-type`, and `truncate-table`. The engine accepts only these exact names and values in v9; it must not interpret arbitrary `yak.schema.*` extensions.

For v9, the legacy global `schema.change.behavior` and `yak.schema.evolution.enabled`/`allow-drop-columns` are not alternative policy authorities and are rejected if supplied alongside the new policy. Version 7/8 definitions keep the exact previous conversion path and execution semantics.

The connector catalog exposes capabilities per **source factory, target factory, execution mode, table-selection mode and event type**, identifying allowed policies and explanatory constraints. This is a server-authoritative contract used by the editor to populate Select options and by save, publication, and runtime admission to reject unsupported combinations. A hidden or disabled UI option never constitutes server-side authorization. The exact response field name may be chosen by the implementing PR but must be tested as a published API.

### Event taxonomy

The fixed event identity includes: `CREATE_TABLE`, `DROP_TABLE`, `ADD_COLUMN`, `DROP_COLUMN`, `RENAME_TABLE`, `RENAME_COLUMN`, `MODIFY_COLUMN_TYPE`, and `TRUNCATE_TABLE`. A single source DDL can contain several typed operations and must be handled as one **preflight unit**, with a replay-stable event ID and source offset. Preflight must reject an ERROR or unsupported component before executing any component of the source statement.

The event representation must distinguish source schema, target schema, table identity transitions, and schema-invariant data operations. It must not classify a rename as a drop followed by an add based only on a before/after schema diff. Unknown or insufficient Debezium metadata fails closed, even when the selected policy is IGNORE.

## Proposed Changes

### Supported path and policy availability

The initial implementation covers **continuous MySQL CDC to JDBC MySQL/PostgreSQL**, including fixed selections and the existing constrained dynamic REGEX selection. Other connectors must not implicitly inherit these options. NORMAL capabilities are as follows:

| Event | NORMAL availability in initial scope | Important constraint |
| --- | --- | --- |
| Create table | Dynamic REGEX discovery only | Reuse existing source-table bootstrap and schema preparation; do not treat every schema-history CREATE as a request to create a target |
| Drop table | Not offered | Never drop a target table in the first release |
| Add column | Offered | Existing nullable ordinary end-position fields; no primary-key change |
| Drop column | Explicitly opt-in, not a default | Existing checked non-key drop and data-loss warning |
| Rename table | Not offered | Source identity and target-route migration are not implemented |
| Rename column | Not offered | No implicit drop-plus-add rewrite |
| Modify column type | Offered only for proven safe widening | Keep the current native-type and key restrictions |
| Truncate table | Offered only after the full runtime and recovery acceptance tests pass | One-to-one target ownership, no multi-table merged sink |

IGNORE and ERROR are advertised only when the source can **reliably classify** the operation and preserve an executable and checkpoint-consistent source/target state afterward. For example, a source rename that cannot be identified reliably is not advertised as a successfully ignored event. Incomplete capability coverage is surfaced explicitly; there must be no UI-only promise of support.

For newly created v9 tasks, the desired defaults mirror the task-level DataWorks editor shown to users: IGNORE for create/drop table, drop/rename column, rename table, and modify type; NORMAL for supported add-column and truncate-table. The service must substitute ERROR or disallow creation when a required default is not supported; it must never silently select an unsafe NORMAL. A dynamic REGEX task must explicitly select CREATE_TABLE=NORMAL and retain its current initial-schema and startup prerequisites; changing away from NORMAL is a task-definition change requiring review, not a silent table-discovery toggle.

### Runtime event processing

1. The MySQL reader must keep DDL capture enabled for per-event evaluation even if all configured policies are IGNORE or ERROR. Debezium's structured table changes classify supported CREATE, DROP, ALTER and table-rename metadata. Column renames require a trustworthy classification mechanism; a pair of indistinguishable drop/add columns is not evidence of rename. Unsupported metadata and multi-table statements without preserved per-table ordering fail closed.
2. Debezium `op=t` TRUNCATE events enter a dedicated ordered source event path. They are not encoded as a schema diff, because a truncated table has the same physical schema. No source offset may be acknowledged before the corresponding policy and coordinated handling are durable.
3. Source-side schema history/row decoder and sink-side column projection must be independently versioned for IGNORE. The schema coordinator fences read input, drains in-flight rows, validates the entire typed DDL operation list, applies permitted target DDL, updates writer projections, and only then commits new source/target state.
4. A mixed ALTER statement (for example ADD COLUMN followed by DROP COLUMN) must be preflighted against every configured strategy before any target DDL. Because MySQL DDL can auto-commit, a partial/uncertain execution failure is **not** assumed reversible; the task fails closed with the known applied operations and a reconciliation instruction.
5. Existing dynamic discovery and the event-classification path must use one policy decision for newly admitted tables. CREATE_TABLE=NORMAL means prepare/admit/bootstrap the matched table. IGNORE means do not start reading or admit it. ERROR means stop before admission. First-run initial target preparation is governed by `initial` and is not retroactively blocked by the runtime CREATE_TABLE setting. Already admitted tables and discovered routes are recovered from checkpointed state, not rediscovered from today's catalog.
6. DROP/RENAME IGNORE must update or invalidate selected-table identities and decoding/projection as appropriate, rather than blindly continuing under stale metadata. Drop-and-recreate under the same name is a new source identity, not automatic reuse of a previous table's checkpoint.

### TRUNCATE durability

TRUNCATE requires a separately named, source-offset-bound operation. The MySQL/PostgreSQL sink must verify that the target table is exclusively owned by the route, fence DML, flush the preceding rows, and execute the dialect-specific operation before allowing later rows.

TRUNCATE is a destructive and potentially auto-committed operation. A crash after the database acknowledged TRUNCATE but before checkpoint durability can make an automatic replay dangerous if later rows already reached the sink. Therefore:

- Persist an operation identity and explicit execution lifecycle marker (including the target and source position) before/with the execution barrier.
- Allow automatic replay only when ordering and the saved state prove that no later rows can be removed by that replay.
- If the result is unknown, **fail closed** and require operator reconciliation; never blindly retry TRUNCATE or mark it successful solely because the table is empty.
- Verify recovery after `before target SQL`, `after SQL before acknowledgement`, `after acknowledgement before checkpoint`, and `after checkpoint` failure points. Startup must reject unsupported serialized state rather than resetting offsets.
- Cross-table aggregation, dynamically changing target identity during the same operation, and target engines without checked truncate semantics remain unsupported.

Any new checkpoint serializer format is explicitly versioned with a tested reader for supported old snapshots; incompatible changes block restored execution with an actionable error. Changes to a running task's v9 DDL policy never hot-apply: a new immutable definition version takes effect only on a new eligible execution.

### Frontend

In the Advanced drawer's DDL configuration, replace the two boolean switches **for v9** with eight compact, consistently aligned, keyboard-accessible Yak UI Select fields in the order shown above. Keep the initial target-schema Select separate. Populate allowed choices and disabled reason from the server's capability response rather than a hard-coded target name check. Provide a short strategy explainer and a destructive-operation warning adjacent to DROP COLUMN and TRUNCATE NORMAL.

Save, reopen, restore, version compare, publish, and target/selection changes must round-trip the whole policy without dropping hidden options. The REGEX selection prerequisite checks actual CREATE_TABLE=NORMAL capability instead of checking a global evolution boolean. Legacy v7/v8 definitions retain their previous effective configuration in a distinct, accurately labelled display until an explicit migration is accepted.

### Error surfaces and security

Report task, event category, source table and offset identity, chosen action, validation or execution phase and safe remediation. Do not log raw DDL values, secrets, row contents or connection credentials. Keep the existing Workspace authorization, connection binding, route ownership, and task revision/publication guards. Failure to access source metadata is a failure, not the absence of a DDL event.

## Compatibility, Deprecation, and Migration Plan

Version 7 and 8 Task JSON and published immutable definitions remain readable and executable **without reinterpretation**. The old switches are not directly equivalent to the new actions: `allowDropColumns=false` historically means unauthorized/fail, not IGNORE, and `evolution.enabled=false` may have disabled source DDL capture instead of deliberately ignoring each type. Automatically mapping them to eight IGNORE values would silently change runtime behavior.

A read-only view of a legacy definition does not append a new parameter version. A user who elects to edit DDL policies is shown a reviewed v9 conversion with an explicit before/after summary and confirmation. The conversion may recommend conservative ERROR defaults for previously unobservable events and must not promise an exact automatic migration when it cannot preserve old semantics. If an equivalent conversion cannot be established, keep legacy execution until a deliberate reconfiguration/new task; do not force a guessed upgrade. The old `schema.change.behavior` entry is removed on **confirmed** v9 migration only. Unrelated metadata edits preserve the previous definition and runtime binding.

The v9 format creates no implied database table or Flyway migration: parameters remain versioned JSON; source schema and checkpoint state changes are independently versioned. Prior checkpoint and published-instance behavior remains pinned to its immutable definition. There is no mixed-version backend rollout guarantee. Documentation and release notes identify how to upgrade, operational limitations and rollback/reconciliation rules.

## Test Plan

- **Contracts:** strict v9 parsing and YAML round-trip; all eight required keys; unknown-value rejection; connector-pair capability matrices; server-side save, publish and admission checks; unchanged v7/v8 behavior and explicit migration.
- **Event classification:** real Debezium MySQL binlog fixtures for single and mixed ALTER, CREATE, DROP, column/table rename, TRUNCATE `op=t`, multi-table/unrecognized events, and stable event IDs. An ambiguous rename must fail closed. A rejected event must not advance its source offset.
- **Semantics:** assert NORMAL, IGNORE and ERROR wherever advertised. Verify mixed ALTER preflight causes zero target DDL before ERROR. Verify source and target schema divergences, nullable column additions, dropped-column DML, widening, mapping/key conflicts, rows before and after DDL, and task-error diagnostics.
- **Database acceptance:** MySQL CDC against real MySQL and real MySQL/PostgreSQL targets, including both supported target dialects, table count/ownership, data before/after TRUNCATE, and the existing dynamic REGEX table-discovery path.
- **Recovery:** fault injection around DDL fences, target execution, writer refresh, source acknowledgement and durable checkpoints. Confirm no stale projection, hidden data loss, blind destructive replay, or source-position skip.
- **Frontend:** accessible Select keyboard interaction, connector-specific options, unsupported-option messages, default values, destructive warnings, save/reopen/version-restore/target-switch preservation, and legacy edit migration in browser integration tests.
- **Regression:** current add/widen/drop tests, static/dynamic source selection, single-table retract/XA restrictions, schema preparation, publication and immutable execution bindings. Run changed Java module tests, UI typecheck/lint/build, PR CI and documented real-database acceptance before marking the implementation PR ready.

Passing compilation, mocks or a saved form round-trip is not equivalent to runtime acceptance. The implementation PR must list missing tests and be kept draft while any advertised destructive behavior lacks acceptance evidence.

## Rejected Alternatives

- **Only replace switches with Select controls:** would store actions that the execution engine cannot honor.
- **Map policies onto global `schema.change.behavior`:** loses per-event decisions and currently supports only `evolve`/`exception`.
- **Treat IGNORE as catching and swallowing DDL exceptions:** risks data corruption, stale field projection and acknowledged offsets for unhandled events.
- **Infer renames from column-list differences:** confuses unrelated add/drop with rename and can lose target data.
- **Forward unvalidated source SQL directly to JDBC:** conflicts with established typed-event security, target capability restrictions and checkpoint idempotence.
- **Blindly retry TRUNCATE after restart:** can erase rows that occurred after a previously successful clear.
- **Overwrite v7/v8 defaults or automatically upgrade running tasks:** changes published behavior and recovery semantics without review.
- **Clone the DataWorks channel matrix or include WARNING by default:** DataWorks' matrix is connector-specific and its alerting contract is not implemented by Yak Ops.
