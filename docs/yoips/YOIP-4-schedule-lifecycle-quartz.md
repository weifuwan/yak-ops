# YOIP-4: Schedule Lifecycle + Quartz Integration

Status: Accepted

Discussion: https://github.com/weifuwan/yak-ops/pull/1626

Decision: [VOTE] Accepted — https://github.com/weifuwan/yak-ops/pull/1626#issuecomment-6144063389

Implementation: Pending

Release: N/A until released

Related FLIPs: N/A

## Motivation

Yak Ops needs periodic submission of published Tasks without coupling time scheduling to
DATA_SYNC or to YakFlow execution. [YOIP-2](YOIP-2-task-execution-lifecycle.md) already defines
Workspace-scoped submission, request replay, immutable Instance versions, resource admission,
and a single verified embedded execution owner. This proposal extends that submission
contract; it does not replace the dispatcher, introduce Task Attempts, or change Runtime recovery.

The initial scope is one Cron schedule per eligible Task Definition. DATA_SYNC permits bounded
JDBC jobs and rejects continuous CDC configurations for periodic submission. Other Task types
can declare their own eligibility. Workflow is a future schedule target, not an execution
engine implemented by this proposal.

Quartz determines when a submission is due. Task submission determines whether to admit it.
Task plugins and YakFlow determine how it executes. A later Workflow engine owns dependency
progression, node retries, and node submission. None of those operations is a Quartz Job.

The contracts below are accepted by the maintainer decision linked above.
The Test Plan is an implementation acceptance requirement, not executed verification evidence.

## Public Interfaces

### Scheduler plugin boundary

Introduce `yak-ops-plugin-scheduler-api` and `yak-ops-plugin-scheduler-quartz` under
`yak-ops-plugins/yak-ops-plugin-scheduler`. Use one application-managed Quartz implementation;
no aggregator module, provider registry, or additional factory-discovery framework is needed.

The API exposes lifecycle start/close, idempotent registration/replacement/removal, registration
inspection for reconciliation, and Cron validation/preview. Registration contains only
Workspace ID, schedule ID, revision, Cron, time zone, and the effective activation boundary.
A trigger notification contains the registered identity/revision, scheduled time, and actual
fire time. A directly injected listener receives that notification. It is not a service locator.

Quartz types, JobDataMap, JDBC objects, Task parameters, credentials, DAO entities, and platform
execution identities do not cross this API. Only the Quartz module imports `org.quartz`.
Registration inspection reports engine registration state and next fire time, not Task status.
The application owns startup/shutdown and injects the listener through ordinary Spring assembly.

### Task submission and eligibility

Add an explicit `supportsScheduling(ReadonlyConfig parameters)` capability to
`ExecutableTaskPlugin`. The default is false; eligibility is checked against parsed parameters
at enablement and again before each admission. This has no network access and introduces no
separate plugin family. A manual-only plugin remains manually executable.

Extend the trusted submission command, internal admission request, and Task Instance metadata
with nullable `scheduleId`, `scheduleRevision`, and `scheduledTime`. They are required together
for SCHEDULE submissions and absent for existing MANUAL submissions. Actual callback time
belongs to the fire record; Instance creation/start/end times retain their existing meanings.
Existing Task REST responses expose the added fields without exposing internal owner tokens.

Only the internal schedule path can construct SCHEDULE provenance. Public manual-run requests
cannot choose a trigger type, forge schedule provenance, select another Workspace/operator, or
bypass final schedule validation. Existing manual Idempotency-Key behavior remains unchanged.

A new scheduled admission selects the latest published Definition version and then fixes that
version on its Instance. Replays return the original Instance/version, even after republishing.
If the Definition changes between preparation and final admission, reject that unadmitted fire
as `DEFINITION_CHANGED`; do not silently reuse parameters from one version with another version.
A subsequent Cron occurrence may use the new version. No automatic replan loop is introduced.

### Schedule REST contract

Use `/api/v1/schedules`, independent of the Data Sync REST namespace. Apply existing response,
pagination, and error conventions. Workspace and operator identity come from authorization,
not request bodies. Reads and mutations require access to the target in the same Workspace.

| Operation | Proposed request | Result |
| --- | --- | --- |
| `POST /page` | Target/status filters and ordinary page controls | Bounded page of schedule summaries |
| `POST /` | `targetType`, `targetId`, `cronExpression`, `timeZone` | New disabled schedule; a second active schedule for the target conflicts |
| `GET /{id}` | Schedule ID | Configuration, revision, registration health, next fire time, and eligibility reason |
| `PUT /{id}` | `revision`, `cronExpression`, `timeZone` | Updated disabled schedule; target identity is immutable |
| `POST /{id}/enable` | `revision` | Persisted enabled intent and current registration health |
| `POST /{id}/disable` | `revision` | Persisted disabled intent; already admitted Instances are unchanged |
| `DELETE /{id}` | Expected `revision` | Atomic disable/delete intent; historical results remain readable under authorization |
| `POST /preview` | `cronExpression`, `timeZone`, optional UTC `after` | Effective `after` and up to five future UTC fire times; no persisted schedule or Trigger |
| `POST /{id}/fires/page` | Outcome/time filters and page controls | Observed fire results, safe reason codes, and authorized Instance links |

Use optimistic revisions for mutations. Repeating enable/disable when already in the requested
state is a no-op with no revision increment; it never changes a stopped schedule back on from
a stale request. State-changing requests with stale revisions conflict. Delete IDs are never
reused. Recreating a schedule allocates a new ID and remains disabled.

List/detail responses distinguish desired `enabled` state from registration health:
`PENDING`, `APPLIED`, or `ERROR`, with a sanitized reason and observation time. APPLIED means
the engine registration matches current intent, including removal for disabled schedules; it
is not an execution-success claim. Unknown/unavailable observations are not APPLIED. An enabled
schedule can report `exhausted=true` and no next fire time for a finite expression.

Saving or enabling does not imply a Quartz registration has already succeeded. Registration
failure leaves the durable intent available for reconciliation and is visible in the response
or subsequent reads. Task-list projections load summaries in batches, not one query per row.

### Persistence and time contract

Add `yak_ops_schedule` with ID, Workspace, target type/ID, Cron, time zone, enabled flag,
monotonic revision, enablement boundary, enabling actor, monotonic `retiredBefore` retention
floor, ordinary audit fields, and deletion marker. Enforce one nondeleted schedule per `(workspace_id, target_type, target_id)` in the
database. Only TASK is accepted initially; reserve WORKFLOW in the design but reject its use.
`targetType=TASK` is distinct from a Task's `taskType=DATA_SYNC`.

Add `yak_ops_schedule_fire` with schedule/Workspace/revision, UTC scheduled time, actual fire
time, resolution time, request ID, resolved outcome, safe reason code, and nullable target
Instance ID. Enforce uniqueness on `(workspace_id, schedule_id, schedule_revision, scheduled_time)`.
Snapshot enough non-secret target identity to authorize historical reads after schedule deletion.
Only ADMITTED has a target Instance ID. Store no row payloads, credentials, execution tokens,
engine JobClients, retry counters, or Task RUNNING/FINISHED state here.

Resolved outcomes are `ADMITTED`, `SKIPPED`, `REJECTED`, and `SUBMISSION_FAILED`. They describe
submission, not execution. A fire is not a ScheduleInstance and does not acquire execution
resources. No future occurrences or unobserved downtime gaps are prepopulated as rows.

Use `Instant` semantics and UTC millisecond precision for scheduled, actual, and resolved times.
Convert to/from existing SQL timestamp types explicitly in UTC. API timestamps carry `Z` or an
explicit offset; a bare local timestamp is not a logical occurrence identity. Cron uses the
stored named time zone. Reject unknown zones instead of accepting a silent GMT fallback.

Proposed system settings are `yak.schedule.max-lateness=PT60S`,
`yak.schedule.reconcile-interval=PT30S`, and `yak.schedule.fire-retention=P30D`. They must be
positive; retention must exceed the lateness window. Lateness changes require owner restart so
Quartz and the business gate change together. These settings are not per-task UI controls.
User-specified start/end windows, calendars, one-shot runs, and backfill are outside this scope.

## Proposed Changes

### Ownership and dependencies

| Location | Responsibility |
| --- | --- |
| Scheduler API | Time-rule input, registration inspection, notification and lifecycle contracts |
| Quartz plugin | Job/Trigger persistence, Cron calculation, notification delivery and engine cleanup |
| `yak-ops-business-schedule` | CRUD, eligibility, authorization, reconciliation and submission orchestration |
| Existing Task business | Definition publication and the sole Task submission/admission implementation |
| DAO `schedule` area | Business schedule/fire records and targeted persistence operations |
| Boot | ScheduleController and lifecycle/wiring; no business rules |

Schedule business depends on the existing Task submission service and the Scheduler API.
Task business does not depend on Schedule business or Quartz. Task unpublish/delete updates
the affected schedule rows through the shared DAO transaction boundary; after commit, schedule
reconciliation applies removals. Final SCHEDULE guards in Task admission also use the shared
DAO contracts, not a callback into Schedule business. Boot does not become a business adapter.
This is an explicit integration between publication and triggering, not a generic resource API.

The [DolphinScheduler Scheduler API][ds-api] and [Quartz assembly][ds-assembly] are references
for the plugin boundary. Its [scheduled submission Job][ds-job] illustrates rechecking business
eligibility before submission. This proposal keeps those business checks out of the Quartz
plugin rather than copying its DAO/Workflow dependencies.

### Lifecycle

| Event | Behavior |
| --- | --- |
| Create or save | Persist disabled configuration; saving never enables scheduling |
| Enable | Check live authority, publication, plugin eligibility and future Cron occurrence; increment revision and record effective enablement time |
| Disable | Increment revision when state changes; reject subsequent new admissions from older callbacks |
| Edit Cron/time zone | Require disabled state; increment revision on changes; remain disabled |
| Delete schedule | Disable and mark deleted atomically; retain authorized history and never reuse the ID |
| Unpublish/delete Task | Disable its schedule in the same business transaction; republishing does not re-enable it |
| Republish a Task | Preserve ordinary publication rules; accepted Instances retain their versions |
| Manual run | Use the existing MANUAL path independently of schedule configuration |
| Resource occupied | Resolve this fire as SKIPPED; no waiting queue or delayed retry after resource release |

Configuration can be saved while a valid target is unpublished, but not enabled. Target changes
require a new schedule. At enablement, record the authorizing actor. Each fire verifies that
actor remains active and authorized for the Workspace/target using existing platform checks;
there is no administrator impersonation or request-thread Workspace context. An unprovable
permission check fails closed. Revoked authority never becomes permission through a stored ID.

Stop affects admission, not execution: an Instance admitted before the stop transaction commits
can be dispatched afterward. Cancelling that Instance is the separate existing cancel operation.
Stopping or deleting a schedule never releases an Instance's resources, including claims held
by RECONCILING Instances. The scheduler does not add a blanket same-Definition exclusion rule.

### Logical fire identity and terminal replay

Define the occurrence tuple as `(workspaceId, scheduleId, scheduleRevision, scheduledTime)`.
The request ID is `schedule_` followed by SHA-256 hex of an unambiguous length-delimited encoding
of that tuple, using epoch milliseconds for the time. It fits the existing request-ID character
and length contract. Use SCHEDULE as the Instance request namespace.

The request fingerprint covers target type/ID and immutable occurrence provenance. Actual fire
time and newly selected current Definition version do not alter the identity of a replay.
Manual fingerprints keep their current semantics. A matching key with conflicting intent is
an explicit idempotency conflict, not a resource-occupied skip.

Read the persisted fire result and existing Instance by request ID before considering new
admission. A committed ADMITTED result returns its original Instance, even if the schedule is
now disabled, expired, or deleted; caller authorization is still required. SKIPPED, REJECTED,
and SUBMISSION_FAILED are terminal for that occurrence. They do not become runnable when the
resource or target later changes. Inconsistent fire/Instance evidence is an integrity error,
not permission to construct another Instance.

[Quartz scheduled time][quartz-context] identifies the expected occurrence; actual fire time
measures callback delay. `fireInstanceId`, random UUIDs, and the current clock are not business
idempotency keys. Read the revision from the fired Trigger's immutable registration data, not
from a newly loaded Schedule or a shared mutable JobDetail. Replacement must not relabel an
in-flight old callback. Null scheduled time and early/nonmatching Cron notifications are rejected.

### Admission transaction and race ordering

The notification listener waits for durable submission resolution only. It does not wait for
a physical Job to finish and does not create an untracked in-memory work queue. Existing
SubmissionService/AdmissionService remain the only route to Task Instance creation.

For new SCHEDULE intent, the outer business transaction and final Task admission use one
physical platform-database transaction. Acquire locks consistently in this order: current
Definition row, Schedule row, occurrence row/key, then execution resource keys in canonical
order. Enable/edit/disable/delete and Task unpublish/delete follow compatible ordering; they
must not acquire a Schedule lock and then wait for a Definition lock. Nonlocking preparation
may precede this transaction, but cannot authorize the final write.

Under those locks, recheck publication and prepared Definition version, enabling authority,
schedule target/state/revision, activation/time eligibility, existing occurrence result, and
resource binding. Include the schedule guard in final Task admission so an internal caller
cannot bypass it by directly choosing SCHEDULE. Persist an ADMITTED fire, its Task Instance,
and all resource claims atomically. Physical dispatch observes only committed Instances.
Do not call an independently committing `REQUIRES_NEW` admission and later append a fire row.

Expected exclusive-resource conflict resolves to SKIPPED under the same occurrence serialization.
Use a savepoint around the existing admission writes, retaining the outer Definition/Schedule
locks: roll back partial Instance/claim writes, then commit the final skip. No earlier callback
may leave a partial Instance or reopen a known skip through a rollback-to-new-transaction gap.
The nested/savepoint boundary must not mark the outer transaction rollback-only for an expected
conflict. Unexpected transaction, connection, or deadlock failures roll back the whole unit;
do not try to commit a skip from an aborted transaction. Implementation must prove this with
the platform's actual transaction manager/database rather than assuming annotation behavior.

If disable commits first, a new old-revision fire cannot be admitted. If admission commits
first, disable preserves that Instance. The same ordering applies to unpublish/delete. A
Definition change detected after preparation produces a specific rejection, not a retry with
silently changed execution intent. No new distributed-lock service is introduced.

Distinguish `RESOURCE_BUSY`, `STALE_SCHEDULE`, `TARGET_UNPUBLISHED`, `NOT_SCHEDULABLE`,
`UNAUTHORIZED`, `DEFINITION_CHANGED`, `BINDING_CHANGED`, `MISSED_WINDOW`, and
`IDEMPOTENCY_CONFLICT` by structured outcomes/reasons. Only a conflict on the actual execution
resource constraint means RESOURCE_BUSY. Catching every DuplicateKeyException or the existing
broad RESOURCE_DUPLICATION error as a skip is incorrect. User-facing text is localized at the
product boundary; decisions never parse exception messages.

### Submission failure, loss, and retention

A known pre-admission failure can resolve to SUBMISSION_FAILED only after absence of an admitted
Instance is established under the same occurrence serialization. A database commit whose result
is unknown is not a failure result: read fire/Instance evidence using the same request ID when
the database is reachable. Never mint a replacement key or report a successful skip on uncertainty.
If storage is unavailable, log/measure the infrastructure error; a durable fire row cannot be
promised. No automatic replacement execution or retry-until-success worker is introduced.

There is a deliberate gap between Quartz recording a firing and business admission. A crash in
that gap may lose an unadmitted occurrence. JDBC JobStore and request deduplication do not make
those transactions atomic. This design promises at most one admitted Instance per occurrence,
not guaranteed delivery of every theoretical Cron time or exactly-once external writes.
A duplicate notification may re-enter only when no terminal evidence exists and it is still
inside the current activation/lateness window. A definitely resolved failure never retries.

Clean final fire records after the configured retention period. Under the same Schedule lock,
atomically advance its monotonic `retiredBefore` floor and delete eligible history strictly before
that floor. New admission below the floor is forbidden even if the clock moves backward; existing
Instance evidence can still be returned. The floor is a retention/deduplication boundary, not a
Cron cursor, next-fire calculator, or recovery queue. Successful Instance request identity keeps
its existing lifecycle/retention guarantees. Activation and lateness checks remain mandatory.
Clock rollback or unverifiable owner time suspends new scheduling until safely re-established.
Retain deletion identity as needed for history, never reuse schedule IDs, and do not enumerate
missed downtime occurrences simply to create audit rows. Uncertain outcomes are not successful skips.

### Quartz storage, owner lifecycle, and reconciliation

Use JDBC JobStore with a dedicated Quartz table namespace in the platform database and
`isClustered=false`. The selected Quartz release and matching vendor DDL are pinned in the
implementation dependency/migration review; this proposal does not upgrade Spring Boot or
select a JDBC driver version. JobDataMap persists only primitive/string registration metadata;
no serialized Spring beans, Task snapshots, or credentials. Schema creation is migration-owned,
not an application startup routine that drops and recreates Quartz tables.

Reuse YOIP-2's single-host, canonical-state-root execution-owner contract. Acquire and verify
that ownership before initializing the active Quartz store/lifecycle; disable automatic Quartz
startup. A losing process neither initializes an active store nor mutates its registrations.
Multiple hosts or alternate state roots using the same Quartz tables remain unsupported; a
successful database connection is not execution ownership. [Quartz warns against concurrent
nonclustered instances on the same tables][quartz-cluster]. Cluster support needs its own
execution-ownership design, not merely changing the Quartz clustered flag.

After ownership and required migrations are ready, reconcile in standby, establish an owner
activation boundary, and start scheduling. Shutdown first closes the new-fire admission gate,
then stops Quartz and drains in-flight admission transactions before relinquishing ownership.
Task execution shutdown/reconciliation stays under YOIP-2. Loss of verified ownership stops
new registration and admission; it does not authorize a replacement Job.

The business Schedule row is authoritative for desired state. Apply changes after its transaction
commits. At startup and every configured reconciliation interval, inspect registrations owned
by this scheduler and apply only missing/changed/removal operations. Never touch another engine's
namespace or clear all Quartz tables. Re-read current state before applying a delayed event;
serialize registration changes per schedule so stale enablement cannot win over newer disablement.
Final admission checks still protect the interval before engine cleanup completes.

Use stable Workspace/schedule Job identity and revision-bound Trigger metadata. An unchanged
registration must keep its next-fire state: periodic delete/recreate would move firing boundaries
and lose valid occurrences. A newly enabled/recreated registration computes its first occurrence
after its activation boundary; reconciliation does not synthesize catch-up runs. Registration
errors are observable and retried by the bounded reconciliation cycle, not a parallel Job queue.
Do not automatically set a user's disabled schedule back to enabled to repair engine state.

### Cron, activation, and misfires

Accept the Quartz Cron dialect, including its optional year field. Validate and preview with
the same pinned Quartz parser and named time-zone conversion used by registration. A valid
expression with no future occurrence can be saved disabled, but cannot be newly enabled. Preview
returns an empty sequence for such an expression. Invalid expressions/zones produce validation
errors, not empty success. The browser does not run an independent authoritative Cron calculator.

Explicitly select [DoNothing misfire handling][quartz-cron]. Align the engine misfire threshold
with the proposed 60-second lateness window, while keeping a business admission check because
misfire handling alone does not reject every delayed callback. Do not use FireAndProceed,
IgnoreMisfires, recovery Jobs, or immediate refire as a catch-up mechanism.

For a new admission, scheduledTime must be strictly after the effective enablement/registration
boundary and the current owner's activation boundary, must be a valid Cron occurrence, and
must not be in the future or below `retiredBefore`. At final admission,
`now - scheduledTime` must be at most max-lateness;
older callbacks resolve to MISSED_WINDOW when observed and storage is available. Existing
terminal evidence is replayed rather than reclassified by these time checks.

On restart, use a fresh activation boundary before opening the admission gate, even if a
persisted Quartz Trigger has a due time less than one misfire threshold in the past. Those
unadmitted pre-activation times do not run. SUBMITTED Instances committed before interruption
remain eligible for existing dispatch; processing them is not Cron backfill. Registration
repair after an outage starts in the future rather than iterating past occurrences.

Daylight-saving gaps/overlaps follow the selected Quartz engine's actual sequence. Do not invent
an extra firing or require two firings at a repeated wall-clock time. Preview and firing tests
compare UTC instants through the transition; formatting may show the schedule's zone/offset.
The initial UI selects Asia/Shanghai for a new schedule and always sends an explicit zone.
Existing saved zones do not change with the browser or server default. All relevant clocks
must be synchronized; tests inject time rather than rely on arbitrary sleeps.

[DisallowConcurrentExecution][quartz-concurrency], when used for the short notification Job,
only serializes that Quartz JobKey. It cannot prevent two already submitted Tasks from running.
Task overlap remains governed by the existing database resource claims, not a Quartz annotation.

### Workflow extension boundary

A future WORKFLOW target submits one Workflow Instance through a Workflow-owned submission
service. That service freezes the Workflow definition, node references, and parameter bindings.
Its dependency engine submits node Tasks through the same Task platform. Nodes are not individual
Quartz Jobs, and a node submission does not activate that Task's independent Cron schedule.

Independent schedules choose the latest published Task version at new admission. Workflow nodes
instead need an explicit internal submission path for the version pinned by the Workflow release.
That extension must validate published immutable snapshots and include version selection in its
idempotency intent; it must not weaken manual/latest-version validation globally. No version
strategy framework, Workflow submission stub, or acceptance of unsupported target types is added
in the initial implementation.

Preserve the root logical scheduledTime through future Workflow/node execution separately from
actual start/end timestamps. Future Workflow provenance must identify the Workflow Instance and
node, not misuse scheduleId as a node ID. This proposal does not implement node retry, DAG state,
calendars, date macros, parameter interpolation, queues, compensation, or historical backfill.

### Frontend restoration and observability

Reuse the existing `yak-ops-ui/packages/yak-ui/src/cron-scheduler` exports and the historical
[v1.2.0 scheduling form/preview][old-editor]. Restore interaction assets, not the retired
Data Sync endpoint contracts, old Attempt models, or old save-and-enable behavior.

Task lists show manual-only state when unconfigured, otherwise Cron, enabled intent and concise
registration status with an independent enable/disable control. An unpublished/ineligible Task
cannot be enabled. Saving configuration and enabling are separate operations. Preserve values
when a save fails; do not report success before the API confirms persistence. Registered summaries
are fetched in batches. Necessary blocking/error reasons are shown without repeated helper text.

Place schedule settings at Task Definition level, using the existing form and Cron picker. Keep
Cron, time zone, and the next five times; preview works before saving a Task, is debounced, and
ignores superseded responses. A valid advanced Cron not representable in visual mode must retain
its original expression rather than be silently rewritten. Misfire, JobStore and thread-pool
settings are not exposed in the task editor.

The selected Instance's status/config/log tabs remain Instance-scoped. Show schedule provenance
and logical time on scheduled Instances; fire history can link to an admitted Instance, while
skipped/rejected fires explain why none exists. The operations center remains an aggregate view,
not a schedule editor or detailed log page. Count admitted/skipped/rejected/submission-failed
fires separately from Task success/failure and avoid counting replays as new occurrences.

Expose sanitized registration errors, callback/admission lag and failed/unknown submissions in
existing diagnostics. Avoid instance/request IDs as unbounded metric labels. Task logs begin
with admitted Instances and use the existing masking/authorization path. Trigger diagnostics
do not create fake Task logs for skipped fires or expose raw Quartz data/database errors.

## Compatibility, Deprecation, and Migration Plan

This accepted design is recorded by a documentation-only PR. The proposal commit creates no
Maven modules, Java classes, frontend code, dependencies, SQL, CI workflows, or enabled schedules.

Implementation adds the schedule API/Quartz modules and business module,
forward-only schedule/fire migrations, nullable Instance provenance fields, and the pinned
Quartz vendor schema. Keep published migrations/checksums immutable; allocate actual migration
numbers against the then-current main branch. Existing Task definitions, datasource JSON,
admission resource keys, operator UIDs, Instance states, log paths and checkpoint bytes remain
unchanged. Historical Instances expose null schedule provenance. No old DATA_SYNC schedule row
is automatically revived or enabled; any future import requires explicit reviewed migration.

Internal submission constructors, response projections and plugin eligibility contracts migrate
with all repository callers. The default-false capability preserves manual plugins' behavior;
independently compiled plugins must review/rebuild for the supported SPI. No compatibility
wrapper restores Runtime/Resource DTO chains or Task Attempts. The REST addition is additive;
existing manual submission/replay semantics are regression-tested.

Deploy the new schema and the single owner before enabling schedules. Disable schedules and
stop Quartz before any rollback to an application version that cannot enforce their contract.
Leave additive tables/history intact; do not use flyway repair or delete Quartz tables as a
rollback shortcut. Existing schedules are not a claim of distributed Task execution.

## Test Plan

The implementation must provide the following executable evidence. None is claimed as passed
by this design PR. Use deterministic clocks/barriers for races and real platform-database
transactions/Quartz JDBC JobStore for persistence behavior; an in-memory mock is insufficient.

| Area | Required acceptance evidence |
| --- | --- |
| API/authorization | Cross-Workspace reads/writes fail; manual callers cannot forge provenance; disabled/inactive enabling actors cannot admit new fires |
| Eligibility | Bounded JDBC is eligible; CDC and default-false plugins are rejected for scheduling; manual execution remains unchanged |
| CRUD | One active schedule per target; stale mutations conflict; repeat enable/disable is a no-op; delete/recreate never reuses an ID |
| Replay | Concurrent/repeated notifications yield one fire result and at most one Instance; differing actual times do not change the key; conflicting intent is rejected |
| Skips | Occupied execution resources produce one durable SKIPPED result; replay after release stays skipped; RECONCILING claims remain effective |
| Transaction | Inject a conflict after a partial claim write; savepoint rollback removes all tentative writes while preserving occurrence locks and the final skip |
| Stop races | Barrier tests cover both commit orders for disable, delete and Task unpublish against admission; stop never cancels a committed Instance |
| Versions | A Definition changed after preparation is rejected without mixing versions; replays retain the original version; old Trigger revisions never adopt current revisions |
| Failure boundary | Fault injection before admission, after business commit, before Quartz acknowledgement, and during commit response proves no duplicate/replacement execution and no false failure/skip |
| Registration | Lost after-commit updates, orphaned/missing/stale registrations, engine errors and reconciliation converge without resetting unchanged next-fire state |
| Restart | Pre-activation occurrences are not admitted even inside the misfire threshold; persisted SUBMITTED Instances still dispatch; no downtime-backfill rows are invented |
| Ownership | A competing process/state-root mismatch cannot initialize an active store or start scheduling; shutdown/ownership loss closes the gate before releasing ownership |
| Time | UTC/Asia time zones, invalid zones, finite expressions, lateness boundaries and DST transitions produce matching preview/actual sequences |
| Retention | Purged skip/failure history cannot reopen an old occurrence; deleted schedules and clock rollback do not bypass time/identity guards |
| Frontend | Existing picker/advanced Cron preservation, stale preview handling, save versus enable, task-level placement, list batching and truthful skipped-fire display |
| Product | Real scheduled JDBC transfer through Submission/Admission/Dispatcher; verify Instance version, provenance, metrics and authorized masked logs; test manual regressions |
| Architecture/migration | No Quartz imports outside its plugin, no business-module cycle, no credentials in registration/history; clean install and upgrade preserve existing data |

Retain existing Spotless, Checkstyle, architecture, logging, migration, quality and real-database
acceptance gates. Extend existing relevant test entry points instead of creating a second CI
framework. Report executed tests separately from path-filtered or skipped jobs.

## Rejected Alternatives

- **Put scheduling back into DATA_SYNC or YakFlow.** Time triggering is a platform capability;
  execution plugins and Runtime retain their existing ownership.
- **Implement Workflow DAG scheduling now, or register every node with Quartz.** Root time
  triggers and dependency progression are different contracts; node execution remains future work.
- **RAMJobStore plus a custom durable trigger cursor.** It is a valid smaller deployment option,
  but JDBC JobStore is selected to retain engine registration state without implementing another
  cursor/recovery mechanism. The business fire record stores submission outcomes, not a cursor.
- **Trust Quartz persistence, a fireInstanceId, or DisallowConcurrentExecution for business
  exactly-once.** They do not atomically admit Task Instances or hold Task execution resources.
- **Catch all submission failures as SKIPPED, or retry with a new UUID.** Both hide failures and
  can change the meaning of the same logical occurrence.
- **Automatically queue resource conflicts or catch up after downtime.** This changes skip and
  no-backfill semantics and requires a separate queue/backfill design.
- **Use a callback-filled Context, Scheduler Factory/Registry chain, generic outbox, or separate
  ScheduleInstance execution lifecycle.** The required owner/caller contracts fit ordinary
  injection, existing admission and a final fire-result record.
- **Commit Task admission independently and append fire results afterward.** That leaves stop,
  skip and unknown-commit races without a single durable authority.
- **Read the latest Task version for every future Workflow node.** Workflow releases must pin
  their node execution intent independently of independent-schedule latest-version semantics.

[ds-api]: https://github.com/apache/dolphinscheduler/blob/7cead6c895dbd0bc9e04ea2300c22c6bdff4fcb9/dolphinscheduler-scheduler-plugin/dolphinscheduler-scheduler-api/src/main/java/org/apache/dolphinscheduler/scheduler/api/SchedulerApi.java
[ds-assembly]: https://github.com/apache/dolphinscheduler/blob/7cead6c895dbd0bc9e04ea2300c22c6bdff4fcb9/dolphinscheduler-scheduler-plugin/dolphinscheduler-scheduler-quartz/src/main/java/org/apache/dolphinscheduler/scheduler/quartz/QuartzSchedulerAutoConfiguration.java
[ds-job]: https://github.com/apache/dolphinscheduler/blob/7cead6c895dbd0bc9e04ea2300c22c6bdff4fcb9/dolphinscheduler-scheduler-plugin/dolphinscheduler-scheduler-quartz/src/main/java/org/apache/dolphinscheduler/scheduler/quartz/ProcessScheduleTask.java
[quartz-context]: https://www.quartz-scheduler.org/api/2.5.x/org/quartz/JobExecutionContext.html
[quartz-cron]: https://www.quartz-scheduler.org/api/2.5.x/org/quartz/CronScheduleBuilder.html
[quartz-concurrency]: https://www.quartz-scheduler.org/api/2.5.x/org/quartz/DisallowConcurrentExecution.html
[quartz-cluster]: https://www.quartz-scheduler.org/documentation/quartz-2.5.x/configuration/ConfigJDBCJobStoreClustering.html
[old-editor]: https://github.com/weifuwan/yak-ops/blob/v1.2.0/yak-ops-ui/apps/web/app/data-sync/task-editor.tsx
