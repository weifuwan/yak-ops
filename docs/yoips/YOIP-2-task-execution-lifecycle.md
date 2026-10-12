# YOIP-2: Task Execution Lifecycle and YakFlow Integration

Status: Accepted

Discussion: https://github.com/weifuwan/yak-ops/pull/1588

Decision: [VOTE] Accepted — https://github.com/weifuwan/yak-ops/pull/1588#issuecomment-6121532796

Implementation: Planned (Stage 1 Task Execution + YakFlow Integration)

Release: N/A until released

Related FLIPs: N/A

## Motivation

Yak Ops provides Workspace-scoped, versioned Task Definitions and a Task Plugin SPI for parameter parsing and validation. YakFlow provides a local PipelineExecutor, JobClient, JobGraph and ExecutionGraph lifecycle, durable checkpoints, and JDBC/MySQL CDC connectors for supported linear pipelines. These capabilities do not yet form an executable product path from a published Task Definition to a persisted execution result.

A Task must be independently executable without requiring a Workflow Instance. The product needs a durable record of each requested execution, immutable definition-version binding, trusted runtime resource resolution, asynchronous job submission, cancellation, diagnostics, and an observable terminal outcome. Manual execution is the initial trigger; scheduling and workflow orchestration must be able to reuse the same execution boundary later.

This proposal establishes the business Task execution contract and its boundary with YakFlow. It does not change the YakFlow checkpoint protocol defined by YOIP-1. The first supported execution mode is an embedded, single-Task, single-source/single-sink pipeline. Distributed workers, Workflow DAG execution, Quartz scheduling, rescaling, and exactly-once external commits are outside this proposal.

## Public Interfaces

### Task Instance and Task Attempt

A **Task Instance** represents one requested run of a published Task Definition. It has its own stable instance identity, Workspace identity, pinned Definition ID and immutable version, Task type, trigger identity, submission/start/end timestamps, lifecycle status, and terminal failure reason where applicable. A new independent run creates a new Instance; it never rewrites the Definition. For a new submission, publication eligibility and the current Definition version are checked and pinned in the same database transaction as Instance creation, serialized against publish, unpublish, and version changes. An idempotent replay reads the original pinned version without reevaluating the current publication state.

A **Task Attempt** records one platform-level execution submission associated with an Instance. Its identity, ordinal, submission/finish timestamps, status, YakFlow JobID when known, and opaque log reference are persisted. A Runtime-internal Execution Attempt or whole-Job checkpoint restart does not itself create another business Task Attempt or change its JobID. The initial embedded implementation creates exactly one platform Attempt per Instance. Process interruption records the outcome of that Attempt; no second Attempt is created automatically. Any later platform-level recovery that submits a new Job requires a separately accepted contract for a new Attempt on a nonterminal Instance, and must never reopen a terminal Instance.

The initial externally visible Instance lifecycle is:

- SUBMITTED: accepted and durably recorded; Runtime execution may not have started.
- RUNNING: execution has been successfully submitted and is active.
- CANCELLING: a cancellation request is pending completion.
- RECONCILING: the prior embedded execution owner's termination cannot currently be established; the platform is reconciling ownership, not restoring or restarting a Job. No new submission or target-claim release is authorized.
- FINISHED: Runtime and required business finalization have succeeded.
- FAILED: execution or safe submission failed.
- CANCELED: cancellation has been confirmed, including cancellation before dispatch.

Terminal states are immutable. RECONCILING is nonterminal, is not a YakFlow JobStatus, and must not be confused with a healthy RUNNING Job or a future automatic RECOVERING state. Its only Stage 1 exit is to FAILED after proving the prior owner stopped and the outcome remains unknown, or to a conclusively verified terminal result if that durable evidence exists. It must not transition directly into a newly launched Job. Updates must be guarded against concurrent callbacks and stale submit/cancel results. Definition publication state is distinct from Instance state: unpublishing prevents new submissions but does not retroactively cancel existing Instances.

A Task Definition does not itself constitute an exclusive execution resource. Distinct valid submissions may create concurrent Instances for the same Definition unless their Task-type-specific declared execution resources conflict. The Task platform must not impose a generic single-active-Instance constraint or serialize all runs by Definition ID.

### Task Execution API

Propose the following HTTP operations, scoped to the authorized Workspace:

- POST /api/v1/task-definitions/{id}/instances: request execution of the currently published immutable Definition version and return the Instance ID without awaiting Job completion.
- GET /api/v1/task-instances/{id}: inspect Instance status and the pinned Definition version.
- POST /api/v1/task-instances/page: list execution Instances.
- GET /api/v1/task-instances/{id}/attempts: inspect submission attempts and log references.
- POST /api/v1/task-instances/{id}/cancel: asynchronously request cancellation.

Manual execution requires an `Idempotency-Key` HTTP header identifying one intentional submission. The UI generates a new, sufficiently unpredictable key for each new Run action and reuses that key for every network retry of the same action. A missing or invalid key is rejected (HTTP 400); one new deliberate Run action uses a new key. The header is a caller-supplied submission identity, not an authentication or authorization token.

The platform persists request identity with a database uniqueness constraint on (workspace_id, trigger_type, request_id). MANUAL, SCHEDULE, and WORKFLOW are distinct trigger namespaces; future scheduling and workflow callers must derive a stable request identity from the underlying trigger event so replay of one event remains idempotent. A normalized submission fingerprint binds the key to the requested Definition and explicit request options. For the same authorized Workspace, trigger type, and key, an equivalent retry returns the original Instance and its pinned immutable Definition version, whether SUBMITTED, active, or terminal, even if the Definition is later unpublished or changed. A conflicting payload using the same key returns HTTP 409 and never changes the existing Instance. The fingerprint must not be silently reinterpreted using the Definition's current version during a retry. Authentication and Workspace authorization are required on every request, including replay.

Idempotency lookup and new Instance/Attempt persistence must be atomic and safe under concurrent requests. The database uniqueness constraint is authoritative. A concurrent insert collision on the same equivalent key is resolved by reading and returning the committed original Instance; it is not mistaken for a separate execution conflict. No duplicate Instance, Attempt, or Runtime submission may be created by an equivalent retry.

A distinct DATA_SYNC request targeting a resource held by another nonterminal Instance is rejected with an execution conflict (HTTP 409), without creating another Instance or queuing it. A valid idempotent replay is resolved before fresh resource-admission checks, so it returns the originally created Instance instead of an erroneous target conflict.

The HTTP response means the request has been persisted, not that the YakFlow Job has started or succeeded. API clients must not receive connection credentials, local checkpoint contents, or raw internal exception details.

### Execution Plugin Capability

TaskPlugin remains the sole Task-type registration SPI, responsible for type, parameter schema version, parsing, and validation. ExecutableTaskPlugin extends TaskPlugin as an optional capability; TaskPluginRegistry remains the only registry. Boot constructs stateless plugin implementations using explicit constructor dependencies, without requiring ServiceLoader construction for executable plugins or creating a second registry.

An executable plugin exposes two distinct operations: a **read-only planning operation** that returns a task-specific TaskExecutionPlan, and a **start operation** accepting the verified plan and a trusted TaskExecutionContext and asynchronously returning TaskHandle. Planning may read authorized metadata but must not open a write session, modify an external resource, submit a Job, or otherwise create external execution effects. The immutable, non-secret plan declares canonical exclusive resource identities and a resource-binding revision/fingerprint; plugins without exclusive resources declare an empty set. The type-specific plan may also carry prepared non-secret execution inputs. This is one optional execution capability, not an additional plugin SPI.

The Task platform generates Instance and Attempt identifiers before the admission transaction so planning can use a complete immutable TaskExecutionContext. It persists the pinned Definition, declared resource claims, and non-secret binding identity, but never serializes live TaskHandle, JobClient, passwords, or connections. Before start, the plugin verifies that effective Source/Sink resources still match the admitted binding, or fails *before* any execution side effect. An execution launched with a valid plan must use that exact verified binding; it cannot silently re-resolve an altered target. Reconstructing a plan for a previously committed but not yet launched Attempt must reproduce and compare the persisted claims and binding identity; a mismatch fails safely instead of redirecting execution. The platform, not the plugin, owns durable claim acquisition and release.

TaskExecutionContext carries trusted Workspace, pinned Definition and version, Instance and Attempt identity. It is not proof of authorization and does not expose arbitrary service lookup, ApplicationContext, PipelineExecutor, Spring beans, or credentials. Task-type-specific resource access and engine dependencies are injected at plugin construction; the trusted resolver independently enforces the persisted Workspace identity.

TaskHandle is an engine-neutral, process-local handle for one launched execution. It exposes a runtime reference when available, asynchronous final outcome, and cancellation; it does not persist business lifecycle or recreate an engine's JobGraph, ExecutionGraph, or checkpoint coordinator. The platform owns Instance/Attempt status and terminal races.

### YakFlow Execution Boundary

The DATA_SYNC executable plugin builds a YakFlow Pipeline using its explicitly injected runtime resources, submits it through the existing PipelineExecutor.execute(Pipeline, Configuration), and adapts the returned JobClient into a TaskHandle. The generic Task Execution platform consumes TaskHandle and does not require a YakFlow Pipeline or expose JobClient to other Task types.

YakFlow remains authoritative for JobGraph, ExecutionGraph, StreamTask state, checkpoint storage format, and runtime-internal restart. Business Controllers and Services must not manipulate ExecutionGraph or Source/Sink Task internals.

## Proposed Changes

### Ownership and dependency direction

Task Definition owns stable task identity, immutable parameter versions, publication, and validation.

Task Execution owns Instance and Attempt persistence, request deduplication, resource admission, durable dispatch arbitration, observation of TaskHandle outcomes, cancellation, and durable business status.

An executable Task plugin plans its declared resources without execution side effects, then starts physical execution from a verified plan and returns a TaskHandle. It does not own durable Instance or Attempt state. For DATA_SYNC, the plugin translates one Source/one Sink configuration into YakFlow Source/Sink transformations, uses its injected trusted Datasource capabilities and Connector APIs, creates a StreamGraph, and invokes PipelineExecutor. The effective physical configuration is supplied by the platform and validated by Runtime, not placed in user-defined Task JSON.

YakFlow owns Job execution and checkpoint/recovery mechanics. Core remains database-neutral; Runtime must not depend on product Controllers, DAO, or business persistence.

### Submission and completion

A new manual submission proceeds as follows:

1. Authenticate and authorize the Workspace; resolve the Idempotency-Key against existing committed submissions. An equivalent replay returns its original Instance and pinned Definition version without launching another Job.
2. Load the published immutable Definition version and reserve Instance/Attempt identifiers. Use the executable plugin's read-only planning operation to determine exclusive resource identities and a non-secret binding revision, scoped to that Workspace.
3. In one database transaction, lock or compare the current Definition publication/version, verify it still matches the plan, atomically insert the Instance and first Attempt in **PENDING** dispatch state, enforce request uniqueness, and acquire all declared resource claims. A stale resource revision or a concurrent conflict aborts admission. No external Job is started within this transaction.
4. After commit, the embedded execution owner uses an atomic persisted compare-and-set to move exactly one Attempt from PENDING to **LAUNCHING**, with an execution-owner incarnation and dispatch token. The condition includes absence of a committed cancellation. Only the winning claim may invoke the plugin's start operation. A failed or duplicate claim never invokes start.
5. The winning dispatcher verifies the persisted plan's resource binding and, if permitted, starts the execution. It persists the returned runtime reference (YakFlow JobID for DATA_SYNC) and observes TaskHandle completion. A fast-finishing bounded Job may reach a terminal business state without an intermediate RUNNING write; a late RUNNING update must never replace that terminal state.
6. Successful completion, confirmed cancellation, or terminal failure is recorded atomically on the Attempt and Instance; DATA_SYNC claims are released only when prior writes are conclusively stopped. Terminal updates are version-/owner-guarded and idempotent.

PENDING and LAUNCHING describe internal **submission coordination**, not new public TaskInstance statuses or YakFlow JobStatuses. The initial product accepts one platform Attempt per Instance. A durable LAUNCHING claim is not itself evidence that the physical Job started: database commit and external execution startup are not one atomic transaction.

Cancellation wins immediately if it atomically closes a PENDING Attempt before LAUNCHING; no start may then occur. Once LAUNCHING has been claimed, a cancellation request records intent and is delivered to the dispatcher. The dispatcher skips startup if it can still prove no execution effects occurred; otherwise it must cancel the resulting TaskHandle and await authoritative termination. Do not mark CANCELED or release claims merely because a request flag was set.

A crash after LAUNCHING, including before receiving or storing JobID, is an **uncertain submission** even when no handle was persisted. The persisted owner token prevents another dispatcher from replaying the Attempt. No timeout, new owner, or HTTP retry may reset LAUNCHING to PENDING and call start a second time. Startup reconciliation follows the fail-closed rule below.

The HTTP response confirms durable admission only, not RUNNING or FINISHED. TaskHandle completion maps confirmed successful work to FINISHED, authoritative cancellation to CANCELED, and execution failure to FAILED. For DATA_SYNC, JobClient.getJobExecutionResult() can be exceptional for both cancellation and failure; the adapter must distinguish them using authoritative Job status and the cancellation result, not exception presence or cancellation-request intent alone.

### Embedded process interruption and Attempt lifecycle

The first implementation uses a single embedded YakFlow executor within the owning Yak Ops JVM. Cross-process JobClient reattachment, replacement submission, automatic Checkpoint restore, and a second platform Attempt are outside Stage 1. YakFlow whole-Job restart from a validated checkpoint remains governed by YOIP-1 and ExecutionOptions.MAX_RESTART_ATTEMPTS, using the same JobID and business Attempt.

**Supported ownership boundary:** Stage 1 runs on one host with one canonical writable execution-state root and no detached or remote workers. The Boot composition root must acquire and hold an exclusive OS-level process lock in that root for the lifetime of embedded dispatch. A database-backed singleton owner record additionally identifies the host/installation and unique JVM incarnation and rejects competing or differently located Boot executors using the same Yak Ops database. Only the process holding both ownership controls may move an Attempt into LAUNCHING. Neither a stale heartbeat, a database lease timeout, a PID value, nor an incremented epoch alone constitutes a write fence. Multi-host and independently configured multi-replica deployment are unsupported and must not enable execution.

**Owner transfer:** An incoming process may replace a persisted owner only after verifying that the previous owner's local process lock has been released **on that same supported host and state root**, and verifying no previous engine task or external write operation can remain active. The exclusive OS lock demonstrates the former in-JVM executor is no longer running on that host, but does not alone prove that an in-flight remote JDBC statement or transaction has completed. When a target database may still process prior writes, the platform must obtain reliable connector/database quiescence evidence before releasing the target claim; otherwise it remains protected. An executor discovered on another host or a different state root cannot be taken over on the strength of its heartbeat expiry.

At startup, persisted nonterminal Instances and Attempts are reconciled before new conflicting jobs are admitted. If the old embedded owner is conclusively stopped and an Instance has no durable successful or canceled result, the single Attempt records an explicit interrupted/unknown-outcome failure and its Instance becomes FAILED. This records a platform execution failure, not an assertion that external JDBC effects were rolled back. Releasing a target claim additionally requires conclusive absence of a remaining writer and an atomic owner-checked terminal update.

If the prior execution owner or any in-flight external write cannot be established safe, the Instance becomes RECONCILING. It remains nonterminal with the original Attempt and exclusive target claim; an equivalent idempotent replay returns that Instance, while conflicting new runs are denied. The API exposes a safe ownership-uncertainty reason rather than an unqualified RUNNING status. A competing Boot process must keep embedded dispatch disabled when it cannot prove exclusive owner rights. There is no forced claim release, speculative timeout-based release, or automatic Job resubmission.

A later intentional Run after a terminal Instance creates a new Instance and first Attempt. A future separate cross-process recovery design may introduce additional Attempts only on nonterminal Instances with explicit execution fencing and checkpoint/external-effect validation. A terminal Instance is never reopened.

### Cancellation and terminal races

Cancel-before-LAUNCHING atomically closes the pending Attempt and prevents Job startup. Once LAUNCHING wins, cancellation records a request and must be forwarded to the resulting TaskHandle, unless the dispatcher proves startup never occurred. CANCELLING means cancellation remains unresolved; it never establishes CANCELED, successful Job termination, or permission to release the target claim.

Terminal results are authoritative and immutable. Completion, failed submission, cancellation, and a delayed RUNNING callback are reconciled with guarded database transitions; a late cancellation cannot rewrite FINISHED or FAILED. A cancellation request racing with a successfully finished Job preserves FINISHED, while a failed Job does not become CANCELED solely because cancellation was requested. A missing JobClient after potential startup is an uncertain outcome, not automatic proof of FAILED with safe release.

Canceling a running Job does not guarantee that a final checkpoint was taken. A future resume/restore operation must separately define compatible state, external side effects, and safe executor ownership.

### DATA_SYNC pipeline

The initial DATA_SYNC execution capability supports:

- A bounded JDBC Source feeding a JDBC Sink.
- A continuous MySQL CDC Source feeding a JDBC Sink.
- A single logical Source, no Transform stage, a single logical Sink, and currently supported local linear YakFlow graph shapes.
- Existing DATA_SYNC supported write modes and table/schema compatibility checks.

The Task Definition stores datasource IDs, namespaces, table selectors, and write mode; it does not store runtime JDBC credentials. A narrow, Boot-injected DATA_SYNC runtime-resource capability resolves authorized connections and table schemas using the persisted Workspace identity. TaskExecutionContext carries that identity but is not a credential or proof of authorization. Resource lookup must enforce Workspace scope without assuming that the HTTP request's thread-local Workspace state propagates to an asynchronous worker.

Operator identities used for recoverable jobs must be stable for the pinned Definition topology. Runtime-specific graph planning and checkpoint capabilities are reused rather than reimplemented in Task Execution.

### DATA_SYNC target-table execution exclusion

Stage 1 requires exclusive ownership of each identifiable target table during an active DATA_SYNC Instance, for both APPEND and UPSERT. A Task Definition is not an exclusive resource: different Definitions or separate runs of the same Definition conflict only when they declare the same target. Nonexclusive Task types and distinct resolved targets may run concurrently. A resource conflict returns HTTP 409 without creating an Instance or queue entry; a valid same-key replay returns the original Instance.

The DATA_SYNC read-only plan resolves a canonical resource identity from the authorized Workspace, saved target Datasource ID, and structured TableId (catalog, schema, table) using Connector namespace rules. The plan also binds **the specific Datasource configuration revisions used to resolve the effective source and sink**, without persisting credentials or secret-derived hashes. Admission persists that identity with the Attempt and resource claim. If either Datasource binding or effective target changes before launch, the plugin must fail before external effects rather than submit a Pipeline using a newly resolved target. Boot's trusted resource adapter needs an explicit Workspace-scoped lookup and a reliable Datasource revision token; the current request-thread WorkspaceContext is not a background authorization mechanism.

Only the platform acquires resource claims, atomically with Instance/Attempt admission and request-idempotency state. Exclusive claims use database-enforced uniqueness, canonical structured identifiers, and deterministic acquisition order for multiple resources. Table names cannot be ambiguously concatenated or indiscriminately case-folded. SUBMITTED, RUNNING, CANCELLING, and RECONCILING Instances retain claims. Terminal persistence and release are atomic, guarded by the owning Instance, and require evidence that the previous execution cannot continue writing.

This identity protects identifiable targets inside one authorized Workspace and Datasource record. Different Datasource IDs or Workspaces that happen to point to the same physical table are not automatically recognized as aliases. Resource exclusion also cannot prevent duplicate output from sequential APPEND runs or at-least-once checkpoint replay. General distributed locks, resource queues, and per-Definition concurrency quotas are outside Stage 1.

### Checkpoint, storage, and operational evidence

YakFlow owns checkpoint serialization, completion, publication, and restore semantics as defined by YOIP-1. Task Execution owns the association between an Instance, its effective configuration, and its checkpoint namespace. Independent new Instances must not share a writable checkpoint directory. A future explicit recovery for the same Instance must retain the pinned Definition version and validate stable UIDs, topology, and state compatibility before restoring.

The initial FileCheckpointStore requires durable local storage accessible to the embedded executor. A checkpoint is not an external transactional commit; supported JDBC and CDC paths retain their documented at-least-once limitations. The presence of checkpoint.bin alone is insufficient evidence of an Instance's final outcome or authorization to start a replacement Job. The first-stage platform does not automatically restore or resume an Instance following application-process interruption.

Each Attempt records a structured failure reason and opaque log reference. Logging and metrics can be obtained from the relevant Runtime/operations sources, but credentials, connection strings containing secrets, and sensitive state must not be included in persisted diagnostic fields or public API responses.

### Integration scope

Manual Task execution is the only required trigger for the initial delivery. Scheduling and Workflow execution can later submit through the same Task Execution API or service with their own trigger identities. They must not directly construct ExecutionGraphs. A Task Instance has no mandatory Workflow Instance ID.

The embedded Runtime is the execution target for this proposal. Worker dispatch, cluster membership, external Flink submission, distributed failover, cross-process JobClient reattachment, and Workflow dependency scheduling are separate architecture decisions.

## Compatibility, Deprecation, and Migration Plan

Task Definition IDs, immutable versions, plugin parameter schema, existing REST definition operations, and workspace isolation remain unchanged. Running Instances continue to reference the version they originally pinned after a Definition is updated or unpublished.

Add Task Instance/Attempt, request-idempotency, resource-claim, owner-incarnation, and atomic dispatch-state persistence through forward-only Flyway migrations. Published migrations are immutable; the current task-definition schema remains authoritative for Definitions. A reliable revision token for Datasource resource-binding changes is required when declaring execution plans. No credentials, secret-derived connection hashes, serialized live JobClient references, or Runtime-internal ExecutionGraph objects are added to the database.

The optional executable capability is additive to TaskPlugin and preserves the Registry's exact-type lookup and duplicate-type rejection. Boot will assemble built-in Task plugins with explicit dependencies instead of relying on zero-argument ServiceLoader instantiation; the active application must not register the same plugin through both paths. The current Boot build includes YakFlow Runtime and CDC connectors only as test dependencies: production execution must explicitly assemble the embedded executor and supported connectors as runtime dependencies without introducing Runtime dependencies into the generic Task SPI. This registration/deployment work is future implementation, not existing behavior. No existing Core Connector SPI, JobClient, PipelineExecutor, YakFlow checkpoint bytes, or user Task JSON shape must change as a prerequisite to the initial product execution path. Unsupported graph shapes and incompatible recovery state must be rejected rather than silently downgraded.

The initial implementation supports only one host, a shared canonical local execution-state root, and an enforceable single embedded dispatch owner. It does not claim distributed execution, exactly-once delivery, automatic process-level failover, or that cancellation performs a checkpoint. Restart reconciliation may terminally fail an Instance only after verifying the previous owner stopped, and may release target claims only after confirming external-write quiescence; no cross-process restore or second business Attempt is implicitly scheduled. Recovery of a still-nonterminal Instance with another Attempt remains a future design.

## Test Plan

Tests must cover the complete product chain rather than infer acceptance from Runtime-only integration tests:

1. **Identity and authorization:** Published definitions can be started within the authorized Workspace. Unpublished, missing, or cross-Workspace definitions cannot be started. Instance version binding is immutable.
2. **Submission idempotency:** Manual execution requires an Idempotency-Key; missing/invalid keys receive HTTP 400. Concurrent or sequential equivalent retries in one Workspace and trigger namespace return exactly the same Instance/Attempt and pinned version, including after completion, unpublication, or a later Definition version change. Conflicting reuse receives HTTP 409. The database uniqueness constraint, stable request fingerprint, and authorized retry lookup prevent double submission; future scheduler/workflow triggers have separate namespaces and deterministic event identities.
3. **Real bounded JDBC:** Controller to Task Definition to execution plugin to YakFlow to real JDBC Source/Sink completes; all expected data is delivered and the Instance reaches FINISHED.
4. **Real continuous CDC:** Controller to MySQL CDC to JDBC Sink processes snapshot and binlog changes, records durable checkpoint progress, remains RUNNING until canceled, then reaches a truthful terminal state.
5. **Failure reporting and outcome mapping:** Invalid resource resolution, Pipeline planning errors, submission failure, Runtime failure, and persistence/update failures cannot produce a false FINISHED state; errors remain observable without leaking credentials. Distinguish JobClient terminal CANCELED from FAILED even when its result Future completes exceptionally in either case, and preserve an authoritative FINISHED result against a late cancel request.
6. **Cancellation and terminal races:** Cancel before the persisted PENDING-to-LAUNCHING transition, during launch before TaskHandle publication, while RUNNING, and against a concurrent Runtime terminal result. Pre-launch cancellation prevents startup when it wins the atomic fence. After a claimed launch, cancellation is delivered to the handle and its final result observed. A bounded Job that finishes before the RUNNING write stays terminal, and a canceled request cannot create an untracked Job.
7. **Submission uncertainty and dispatch fencing:** Race two dispatchers for one PENDING Attempt and prove only one atomic LAUNCHING claim can start the plugin. Simulate process failure before claiming, after claiming but before physical start, after Job start but before JobID persistence, and after Runtime completion but before the business terminal update. A restart must never replay a previously claimed Attempt or create a second Attempt; an unknown post-start outcome retains claims until its ownership is reconciled. Confirmed dead execution ownership with unknown outcome produces explicit failed Instance/Attempt evidence without claiming external rollback.
8. **Checkpoint isolation:** Independent Instances use distinct checkpoint namespaces. Completed checkpoints remain valid according to YOIP-1 but are not automatically restored or resubmitted after application restart in the initial product implementation.
9. **Execution ownership and claim safety:** Persist Instance/Attempt identity, timestamps, JobID and log reference without secrets. Verify that two Boot JVMs sharing the supported local state root cannot both acquire the OS execution lock, and that a competing or differently located process cannot take the database owner slot and dispatch. Confirm that a released local lock proves in-JVM termination but is not treated as evidence that a remote DB statement has quiesced. If ownership or external-write quiescence is uncertain, transition to RECONCILING, expose a safe reason, retain the Attempt and target claim, and block conflicting writers. A same-key replay returns the existing Instance. Terminal interruption and claim release require owner-checked proofs and atomic updates, not elapsed time or force-release.
10. **DATA_SYNC admission and generic concurrency:** Two simultaneous requests for different DATA_SYNC Definitions writing the same identified target admit only one Instance; the other receives HTTP 409 before launch. Two distinct Run requests of the same DATA_SYNC Definition conflict on its target claim; distinct targets may run concurrently. Verify with a nonexclusive test plugin that same-Definition parallelism is allowed. Test APPEND and UPSERT, deterministic claim acquisition, request replay, claim retention across every active state including RECONCILING, and owner-checked terminal release. Mutate a Datasource's effective target or revision between planning, admission, and physical start; assert no Job launches against an unclaimed target. Unsupported alias detection is not claimed.
11. **Plugin composition, Workspace and version:** Boot injects each executable plugin's required dependencies and registers it once in TaskPluginRegistry; duplicate type or missing dependency fails startup. Definition-only plugins continue to work. Test that planning is read-only, TaskExecutionContext has no service locator, asynchronous Datasource resolution uses explicit Workspace authorization rather than a request ThreadLocal, and a concurrent publish/unpublish/version update cannot alter the pinned Instance snapshot. Verify product Boot runtime/CDC dependencies are present in production classpaths.
12. **Release acceptance:** Run real-database product E2E as a required executable gate. A green unit build or connector-only acceptance job is not reported as product E2E.

## Rejected Alternatives

- **Require one Workflow Instance for each Task:** Independent Task execution is a platform feature, and a Workflow is only one potential caller.
- **Submit directly from TaskDefinitionController or DefinitionService:** Definition CRUD, HTTP processing, and long-running Job execution have different ownership and transaction lifecycles.
- **Require all plugins to produce a YakFlow Pipeline:** Executable plugins may use different engines, while DATA_SYNC uses the existing YakFlow PipelineExecutor.
- **Maintain two independently registered Task plugin SPIs:** Optional execution capability uses the same TaskPluginRegistry and exact Task type.
- **Resolve runtime services through TaskExecutionContext:** Dependencies are explicit at Boot composition; a generic service locator would obscure ownership and authorization.
- **Require a no-argument ServiceLoader constructor for executable plugins:** Explicit dependencies belong to plugin construction, while Boot controls application composition.
- **Make a Task plugin own durable business execution state:** The plugin may start physical execution and return TaskHandle, but the platform owns Instance/Attempt persistence and lifecycle decisions.
- **Derive target claims after executing start():** That permits external writes before admission and cannot prevent DATA_SYNC target-table conflicts.
- **Retry a LAUNCHING Attempt after claim timeout or missing JobID:** Database state cannot prove whether external execution already started; the initial platform must fail closed.
- **Infer executor death from a database heartbeat timeout alone:** A still-running JVM may continue JDBC writes without renewed ownership; Stage 1 requires verifiable single-host process ownership and external-write quiescence.
- **Duplicate YakFlow execution state or checkpoint coordination in business modules:** The Job and its physical task lifecycle remain Runtime concerns.
- **Treat a persisted JobID as sufficient for embedded process recovery:** It does not restore an in-memory JobClient or prove the outcome of an uncertain external submission.
- **Automatically rerun every nonterminal Instance after application restart:** An uncertain prior Job may already have committed external effects.
- **Treat a published checkpoint as authority to create another platform Attempt after process death:** Checkpoint publication does not prove Job termination or a safe external replay, and embedded JobClient cannot be reattached by persisted JobID.
- **Reuse a terminal business Instance for a later manual run:** Each independent intent produces a new Instance; terminal records remain immutable.
- **Allow simultaneous DATA_SYNC writers to an identifiable target table:** APPEND and UPSERT can interfere across different Definitions, so an exclusive durable admission claim is required.
- **Use an in-memory target lock or timed claim expiration:** Neither protects admission across requests and restarts without risking concurrent external writes.
- **Use Definition ID as the idempotency identity:** A successfully completed Definition must support later intentional runs; a stable request key distinguishes retries from new execution intents.
- **Treat a new Idempotency-Key as proof of safe parallel execution:** Distinct submissions still undergo DATA_SYNC resource-admission checks and may be rejected without being queued.
- **Enforce one active Instance per Task Definition globally:** A Definition is a reusable immutable configuration; exclusive access is defined by the Task type's actual execution resources, not by the Definition ID.
- **Introduce full Workflow, Quartz, Master/Worker, or distributed Job services in this proposal:** These can be layered on the stable Task execution contract when separately designed and verified.
