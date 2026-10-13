# YOIP-2: Task Execution Lifecycle and YakFlow Integration

Status: Accepted

Discussion: https://github.com/weifuwan/yak-ops/pull/1588

Decision: [VOTE] Accepted — https://github.com/weifuwan/yak-ops/pull/1588#issuecomment-6121532796

Implementation: Task Instance execution platform (https://github.com/weifuwan/yak-ops/pull/1618)

Release: N/A until released

Related FLIPs: N/A

## Motivation

Yak Ops executes published, versioned Task Definitions through a Workspace-scoped Task platform.
YakFlow provides the physical Job, ExecutionGraph, checkpoint coordination and connector execution.
The platform records one durable execution intent, submits one physical Job, and exposes its
business lifecycle. Runtime owns its internal recovery and subtask execution attempts.

Manual execution is supported independently of Workflows. Scheduling and Workflows may invoke the
same submission service with stable trigger identities. The embedded deployment uses a single
verified execution owner, a local state directory and resource-specific admission claims.

## Public Interfaces

### Task Instance

A Task Definition is a published, versioned configuration. Each deliberate Run creates one
Task Instance bound to a fixed Definition ID, version, Workspace, trigger type and request identity.
An equivalent submission replay returns the same Instance; a separate deliberate Run creates
a new Instance. The business Instance owns its physical Job reference and terminal result.

An Instance stores:

- Definition ID and immutable version, Task type, Workspace, trigger and idempotency identity
- Execution status, failure code, start/end timestamps and optimistic state version
- The embedded owner incarnation and opaque one-time dispatch token
- Runtime engine and Job ID once the Job has been observed
- A non-secret resource-binding revision used by execution admission

The dispatch token is never exposed in the public API or diagnostics. Live JobClient and
execution-graph objects remain process-local. User-defined Task configuration contains no
trusted runtime ownership or credentials.

### Instance lifecycle

| Status | Meaning |
| --- | --- |
| SUBMITTED | The run is durably admitted and waiting for its first dispatch claim |
| LAUNCHING | The single verified dispatcher owns the launch; physical startup may be in progress |
| RUNNING | A physical Job reference has been observed |
| CANCELLING | Cancellation intent is recorded; physical termination is pending |
| RECONCILING | Previous execution ownership or external writes cannot yet be safely resolved |
| FINISHED | The Job completed and the result was durably committed |
| FAILED | The Job failed or a safe pre-start rejection was confirmed |
| CANCELED | Cancellation has been confirmed, including before launch |

FINISHED, FAILED and CANCELED are terminal and immutable. RECONCILING is nonterminal and
retains its resource claims. The platform never treats an observed Job ID or elapsed time as
proof that an external writer has stopped.

The Task API exposes:

- `POST /api/v1/task-definitions/{id}/instances`: admit a run with an `Idempotency-Key`
- `POST /api/v1/task-instances/page`: list Instances in the authorized Workspace
- `GET /api/v1/task-instances/{id}`: inspect status, engine, Job ID and result
- `GET /api/v1/task-instances/{id}/definition`: inspect its pinned Definition snapshot
- `POST /api/v1/task-instances/{id}/cancel`: request safe cancellation

The submission response contains the Instance ID, pinned Definition version, current status
and replay indicator. A response confirms admission, not physical Job startup.

## Execution and Persistence

### Admission

The Task plugin parses and validates its parameters and prepares a read-only
`TaskAdmissionPlan`. The plan declares the effective non-secret resource binding and
canonical exclusive resources. Admission verifies the published Definition and resource
revision, then inserts the Instance and acquires its resource claims in one transaction.

The database enforces uniqueness of `(workspace_id, trigger_type, request_id)` and
`(workspace_id, resource_type, resource_key)`. A repeated request with identical intent
returns the original Instance and cannot dispatch a second Job. Conflicting input reusing
the same request identity is rejected. Concurrent target writers are rejected before start.

### Dispatch and observation

The embedded dispatcher holds an operating-system process lock and a database execution
owner identity. It atomically claims one SUBMITTED Instance, changes it to LAUNCHING, and
persists its owner incarnation and one-time dispatch token.

A winning dispatcher verifies the pinned Definition version, planned resource revision and
held claims before invoking the plugin start operation. The plugin returns a `TaskHandle`.
The observer records the engine and Job ID, tracks completion, and forwards cancellation
through that handle. The winning token guards Job-reference persistence, terminal transitions
and uncertain-execution reconciliation.

A SUBMITTED Instance may be canceled and finalized without invoking the plugin. Cancellation
after the LAUNCHING claim records CANCELLING, forwards the request when a handle exists,
and waits for authoritative termination before releasing resource claims. A delayed start
observation may record its Job ID without replacing the CANCELLING state.

The platform stores the authoritative terminal status and releases claims in the same
transaction. Stale callbacks cannot update terminal Instances or release another launch's
resources. The engine's structured failure category is persisted without sensitive details.

### Restart and reconciliation

A pending SUBMITTED Instance can be claimed after process restart. An Instance already in
LAUNCHING, RUNNING or CANCELLING belongs to its recorded owner incarnation; startup
reconciliation checks the prior owner and records RECONCILING when physical execution
outcome is uncertain. In that state, the Instance retains its resource claims and cannot
start another Job.

The embedded deployment requires one host and a canonical writable state root.
Ownership checks guard against competing executors. Confirmed terminalization and claim
release require evidence that prior external writers are no longer active. Job resubmission
across process restarts is outside this execution contract.

## YakFlow Runtime Responsibility

One Task Instance owns one YakFlow Job ID. YakFlow controls JobGraph, ExecutionGraph,
subtask deployment, restart and Checkpoint. Runtime restart attempts remain inside the
same Job and Instance, using verified completed checkpoints. A new deliberate Run creates
another Instance and uses an isolated checkpoint directory.

For bounded JDBC execution, restart is disabled by default. For continuous CDC,
configured restart attempts are allowed only after a completed, compatible checkpoint.
Runtime recovery does not authorize the Task platform to issue a replacement Job after
the owning application process terminates.

The Task plugin produces a YakFlow Pipeline using trusted injected connector resources.
The Task platform uses `TaskHandle` and remains independent of ExecutionGraph internals.
Core and Runtime do not depend on product DAOs, Controllers or business persistence.

## Migration and Acceptance

The forward-only Task Instance migration adds launch ownership and Job reference fields to
`yak_ops_task_instance`. It transfers the existing single-submission ownership, Job ID and
observed timestamps from the execution submission records, then keeps Instance as the
authoritative lifecycle table. Published migrations retain their original contents.

Acceptance covers:

1. Equivalent request replay returns the same Instance, including after Definition changes.
2. Two concurrent dispatchers cannot launch the same Instance twice.
3. Cancellation before claiming prevents physical execution and releases resource claims.
4. Cancellation during startup preserves CANCELLING until physical termination.
5. Terminal callbacks are token-guarded and release claims only after confirmed completion.
6. Embedded-process interruption retains protection for uncertain external writes.
7. JDBC and CDC pipelines execute through YakFlow with the correct pinned configuration.
8. Runtime internal restart and checkpoint recovery do not create another business Instance.
9. Independent Instances use distinct checkpoint directories and cannot steal target claims.
10. Public endpoints and UI expose Instance state and the engine Job ID directly.

## Task Instance execution logs

Every admitted Task Instance owns one stable execution log path. The path is relative to the
application-controlled Task log root and is persisted with the Instance, independently of its
YakFlow Job ID. An idempotent replay does not create another log identity. Historical Instances
without a log path return empty content.

Task execution binds the Instance ID as the `taskLogId` SLF4J MDC key. YakFlow carries the ID as
`ExecutionOptions.LOG_ID`, so its Job, StreamTask, Source and Checkpoint threads write to the
same Task log across runtime-internal retries. Source fetchers and asynchronous platform
callbacks bind the identity within their own worker threads and clear it when their thread
exits. Embedded task execution remains the sole physical Job submission for an Instance.

Logback routes task-owned events through `SiftingAppender` and `MDCBasedDiscriminator`. A
TaskLogFilter rejects events without a valid Instance ID and honors
`TaskLogMarkers.excludeInTaskLog()`. SensitiveDataConverter masks credentials in both ordinary
messages and exception stacks before they reach user-visible files. These diagnostic rules
coexist with the application's rolling `yak-ops.log` and static logging checks.

A Workspace-authorized caller reads bounded log segments via
`GET /api/v1/task-instances/{id}/logs` with an optional opaque cursor and a page size. The
`/logs/download` endpoint streams retained archives and the current log without buffering
all bytes. Paths are resolved by the service from persisted Instance identity; callers cannot
supply absolute file paths. Active tasks remain readable as new records arrive, while
completed Tasks retain their files according to the configured local storage policy.

Task logs are per-Instance diagnostic information. The platform does not introduce a Task
Attempt table, a remote logging RPC, or a second Runtime recovery policy for log delivery.
