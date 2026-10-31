# YOIP-1: YakFlow Checkpoint and End-of-Input Semantics

Status: Accepted

Discussion: https://github.com/weifuwan/yak-ops/pull/1577 (review fixes); https://github.com/weifuwan/yak-ops/pull/1576 (convergence); https://github.com/weifuwan/yak-ops/pull/1575 (initial)

Decision: [VOTE] Accepted — https://github.com/weifuwan/yak-ops/pull/1577#issuecomment-6117269913

Implementation: Implemented (supported local Runtime)

Release: N/A until released

Related FLIPs: [FLIP-27](https://cwiki.apache.org/confluence/spaces/FLINK/pages/95653746/FLIP-27+Refactor+Source+Interface), [FLIP-131](https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=158866741), [FLIP-147](https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=184617665)

## Motivation

YakFlow's local Runtime already supports mailbox-driven Source/Sink tasks, aligned barriers, a versioned local checkpoint store, whole-job restart from a completed checkpoint, and normal end-of-input flushing. Today, `SourceReader.END_OF_INPUT` leads directly to `finishTask()` and physical output completion. Once a Source task finishes, periodic checkpoint triggering stops; finished downstream tasks cannot acknowledge another checkpoint.

This is adequate for current local at-least-once JDBC execution, but it cannot support a final checkpoint after logical input completion or checkpoints while some operators have already finished. In particular, a completed checkpoint is not the same as successful delivery of completion callbacks, a finished operator is not necessarily a terminated physical task, and checkpoint persistence is not an external Sink commit.

This YOIP defines these boundaries and their recovery semantics. It does not claim they are already implemented.

## Public Interfaces

- **Core Connector API:** Keep `Source`, `SourceReader`, `SplitEnumerator`, `InputStatus`, `Sink`, and `SinkWriter` unchanged. `END_OF_INPUT` remains a Reader-local result.
- **Internal Runtime protocol:** Specify logical `EndOfData` separately from physical `EndOfPartition`. Introducing actual events is deferred until they have an execution and checkpoint consumer.
- **Checkpoint format:** Runtime writes one complete v5 checkpoint metadata layout, keyed by stable operator UID (and subtask index), and atomically publishes a CRC-protected v6 latest pointer. Historical v1-v4 checkpoint formats are not readable by the new runtime; migration requires an explicit stop-and-reinitialize plan after resolving prepared XA transactions.
- **Product interfaces:** No REST, plugin SPI, persisted task-definition, Flyway, frontend, or user-facing configuration changes. No new public Job status is proposed.

## Proposed Changes

### Scope and implementation boundaries

**Stage A — existing local lifecycle:** Preserve the current supported graph types and at-least-once JDBC behavior. Define and test EOF, barrier decline, Sink final flush, checkpoint publication, notification failure, and Job completion independently. Do not require a new `EndOfData` event or Final Checkpoint just to describe the semantics.

**Stage B — checkpointing with finished tasks:** Implement separate logical/physical termination, checkpoint participation after operators finish, a required final checkpoint for checkpoint-enabled bounded jobs on supported paths, finished-state persistence, and finished-on-restore execution. Enable each behavior only together with its corresponding recovery tests; do not turn it on for unsupported inline chains or graph shapes.

Distributed execution, multi-source/multi-input scheduling, rescaling, unaligned checkpoints, and mixed bounded/unbounded topologies are outside the initial implementation. This design should not prevent them, but this YOIP does not claim they work.

### End-of-data and normal completion

The intended ordering for a bounded execution is:

```text
NoMoreSplits + all assigned Source splits exhausted
  -> SourceReader END_OF_INPUT
  -> operator finish (may emit final records)
  -> EndOfData on each outgoing channel after its final record
  -> downstream consumes all records and EndOfData
  -> downstream operator / Sink Writer finishes
  -> required final checkpoint and/or required external finalization completes
  -> EndOfPartition; Task completion and resource cleanup
  -> all required executions completed -> Job FINISHED
```

This ordering describes dependencies, not a single synchronous call chain. `NoMoreSplits` means there will be no new assignments; it does not prove existing splits are consumed. `EndOfData` stops **records**, not ordered checkpoint-control events. Downstream acknowledges `EndOfData` only after processing preceding records on that channel, including output emitted by an upstream `finish()`; merely enqueuing the event is insufficient. `EndOfPartition` terminates the physical channel and its control traffic.

For each successful execution attempt, `finish()` runs at most once after complete logical input, and may emit additional output. Failure and cancellation must not run normal final flush or implicitly commit pending output. `close()` releases resources and is not a substitute for `finish()`. Single-task operator chaining needs equivalent logical guarantees without synthetic network events.

### Final checkpoint and Job FINISHED

Checkpoint completion means all required participants acknowledged a consistent cut and the checkpoint store published recoverable metadata. It is **necessary but not sufficient** for `JobStatus.FINISHED` **only when the supported bounded execution requires a final checkpoint**. Jobs with no such requirement may finish without creating one.

When Stage B is implemented and verified for a supported bounded job **with `CHECKPOINTING_INTERVAL > 0`**, a final checkpoint must cover the terminal data boundary before normal termination. A checkpoint taken **before** that boundary cannot be treated as final. Runtime must arrange the terminal attempt without waiting indefinitely for the next periodic interval.

`RESTORE_LATEST` requests restoration from a previously completed checkpoint; it does **not** enable periodic checkpoint scheduling. In particular, `CHECKPOINTING_INTERVAL=0` with `RESTORE_LATEST=true` does **not** require an automatic final checkpoint. Manual checkpoint requests remain governed by existing Runtime support. A future Sink that requires checkpoint-based terminal commit must be rejected at planning if the terminal checkpoint protocol/configuration is unavailable; Runtime must not silently report success without the required commit.

Without a required final checkpoint, successful data processing and required output finalization suffice. Existing JDBC at-least-once jobs retain their normal `flush(true)` completion path until the Stage B protocol is implemented and verified; this proposal does not introduce a final checkpoint for them immediately.

`ExecutionGraph` remains the only owner of `JobStatus.FINISHED`. It may report success only after all required Task completions, finalization obligations, and cleanup have succeeded. A successful checkpoint never directly changes Job status. The current JDBC `flush(false)` and `flush(true)` may commit records outside checkpoint transactions, so neither checkpointing nor a final flush implies exactly-once delivery. Recoverable external commit protocols belong to a separate Sink YOIP.

### Stage B terminal checkpoint protocol

- **Completion fence:** A logical `EndOfData` does not terminate the Task mailbox. Required downstream data-consumption acknowledgements, final output, and any necessary Sink finalization must be accounted for before a terminal checkpoint can complete. Do not wait for physical Task `FINISHED` before triggering a checkpoint that those Tasks must still acknowledge; physical `EndOfPartition` follows the required final checkpoint.
- **Trigger roots:** Build a consistent checkpoint plan from active/finished Executions. A logically finished but still running Source may emit a control barrier through its mailbox without polling more records. If its physical Task is already finished, the nearest running downstream Tasks with no running upstream become checkpoint roots. Do not call `pauseAndEmitBarrier()` on terminated Source Tasks, await ACKs from terminated Tasks, or fabricate barriers/ACKs; channels already physically finished are accounted for as terminal in the plan.
- **Finished-operator ACK:** A Task that has logically finished but still participates in checkpointing must acknowledge through a finished-state path, not re-execute its operators. In particular, current `SinkWriterOperator.snapshotState()` rejects calls after `finish()`: Stage B must not use its normal writer snapshot/`flush(false)` path after final `flush(true)`. A finished marker is valid only after all final records and required recoverable state/commit obligations are covered; otherwise retain an actual recoverable state or decline the checkpoint. Never call `finish()` or `flush(true)` again to retry a checkpoint.
- **Checkpoint/EOF race:** If a terminal event prevents the planned barrier cut, decline only that attempt and re-plan from the latest Execution state. Normal data stays intact. A new plan must not block on a finished Source or a Writer already closed for records.

### Completed checkpoint storage and task-definition identity

The local Runtime uses two distinct owners. `PendingCheckpoint` tracks planned Source/Operator ACKs and cannot be persisted until every required participant acknowledges; `CompletedCheckpointStore` exposes only durably published state. The local filesystem implementation stores immutable `checkpoints/chk-<id>/_metadata` files, then atomically replaces `checkpoint.bin` with a small CRC-protected pointer to the latest completed ID. It fsyncs checkpoint bytes and both metadata directory entries before reporting successful publication. By default it retains the latest two checkpoint metadata directories; only the newest published pointer is selected for automatic recovery, with no silent fallback.

The metadata payload uses format v5 and always contains Enumerator State, Reader Splits, assignments, Operator States, finished Operator/Subtask markers, and optional Schema Manager State. The pointer is format v6. A damaged pointer, missing metadata, CRC mismatch, unsupported version, or wrong task-definition identity is a recovery failure—not permission to restart MySQL CDC from another binlog position.

The Data Sync submission layer binds the checkpoint to trusted workspace, Task Definition ID, definition version, and Instance ID. This identity is included in the Runtime graph-signature digest, not embedded in MySQL CDC Split or Enumerator state. A new definition version or another task instance must not restore even if the physical job topology happens to match. Generic Runtime jobs without a trusted Task Definition identity retain topology-only validation.

**Upgrade boundary:** Existing `checkpoint.bin` version 1–4 files are not migrated in place. Stop the task, settle or recover any prepared XA transactions, preserve the old state for investigation, and explicitly decide whether to start a new instance. Do not delete old checkpoints and silently resume with `LATEST_OFFSET` or `INITIAL`: either action could lose or duplicate previously committed data. The existing local file layout is not byte-compatible with Apache Flink's state backend.

### Checkpoint failure, publication, and notifications

- A missing barrier caused by normal EOF **declines that checkpoint** and preserves all records; it does not by itself fail a healthy legacy bounded Job. If a final checkpoint is required, another valid attempt must be obtained or normal completion must fail.
- A transient failure **before publication** may retry the checkpoint in the same live Execution Attempt only when state, final output, and external commit ownership remain safe. For a **required final checkpoint**, permit at most **one in-attempt retry** (two total terminal checkpoint attempts), each bounded by existing `CHECKPOINTING_TIMEOUT`, with an overall final-checkpoint budget of at most **twice that timeout**. Exhaustion fails the current Attempt; `ExecutionGraph` then applies existing `ExecutionOptions.MAX_RESTART_ATTEMPTS` (default zero) only if there is a safe completed recovery point. Do not repeat `finish()`/`flush(true)` or add a user-facing final-checkpoint retry setting. Actual Task/Sink failures and post-publication notification failures are not in-place checkpoint retry candidates.
- After all required acknowledgements, **successful durable publication is the checkpoint completion point**. A timeout or `notifyCheckpointComplete` exception **after publication** cannot revoke it. If CP-100 was published, recovery must prefer CP-100 rather than automatically return to CP-99.
- Completion callbacks are post-publication signals, not a second checkpoint commit phase. A correctness-critical callback or asynchronous follow-up delivery failure fails the current Attempt **even if any Source has already finished**; a Source EOF guard may suppress benign pre-publication checkpoint decline, never a post-publication critical error. Required asynchronous delivery outcomes must be observed by the Attempt/Job completion fence so they cannot arrive unnoticed after `JobStatus.FINISHED`. Generic re-invocation of `notifyCheckpointComplete` is not safe unless that implementation explicitly guarantees repeatability. Auxiliary metrics/cleanup errors that do not affect correctness do not automatically fail a Job.
- `ExecutionGraph` controls whole-job restart using the existing attempt limit and a validated, compatible latest completed checkpoint. The existing default maximum restart attempts remains zero. If the latest checkpoint is invalid, its publication is uncertain, or the required recovery/commit protocol cannot be proven safe, fail clearly; do not silently fall back to older state or fabricate a successful finish.
- Checkpoint publication outcome and post-publication notification outcome must be distinct: a trigger's durable-completion result cannot be converted to an **unpublished checkpoint failure** by a later listener exception; that exception is independently delivered to the Attempt failure path, even during EOF/teardown. The single-file `checkpoint.bin` store is not assumed to retain CP-99 after publishing CP-100.

### Finished-operator state and recovery

A Task's **logical operator finish**, its **physical Execution FINISHED** status, and a **durably published Fully Finished marker** are distinct facts. `SourceReader.END_OF_INPUT` or one `finish()` return value alone is insufficient.

The Checkpoint Coordinator determines the finished-state metadata from a consistent checkpoint plan and Task acknowledgements. A fully finished logical operator requires all of its parallel subtasks to be demonstrably complete, with every final output either covered by the consistent cut or safely owned by recoverable downstream state/obligations. A partially finished operator cannot be promoted to Fully Finished. Partially finished state is acceptable only when the remaining work and state distribution can be safely restored; otherwise decline the checkpoint.

Flink distinguishes Task `FINISHED` at checkpoint planning time from a still-running Task that reports that its operators have called `finish()` in its snapshot. YakFlow should preserve that distinction: **an operator may have finished in memory without being Fully Finished in the checkpoint**. No later event may retroactively change a published snapshot.

On restore, the published checkpoint is authoritative. Fully finished operators **must not re-execute their business logic**; Runtime may still deploy lightweight finished-on-restore physical tasks for downstream EOF and checkpoint control. Unfinished operators restore their captured state and continue. Do not reread completed Source splits, rerun an already recorded operator final output, or repeat a finished Writer's final flush. However, pending external commit obligations must remain recoverable; a Finished Writer marker does not silently complete a separate Committer.

Older checkpoints without finished metadata retain their previous meaning. No completed state may be inferred from missing bytes, final logs, previous Attempt memory, or a merely successful notification.

## Compatibility, Deprecation, and Migration Plan

The proposal leaves existing Source/Sink signatures, JobStatus values, task-definition JSON, Flyway history, and ordinary local at-least-once execution unchanged. Stage B changes are opt-in only through proven supported Runtime capabilities, not a misleading user setting.

A future checkpoint format must explicitly version fully/partially finished state, preserve reading local format versions 1 and 2, validate topology/UID/serializer/parallelism compatibility, and atomically publish a recoverable snapshot. Existing `FileCheckpointStore` remains single-file until a separately justified retention design is accepted. Corrupt or incompatible state must fail closed; neither silent downgrade nor implicit migration is allowed.

The final checkpoint protocol is not an exactly-once guarantee. Any transaction preparation, idempotent external commit, and uncertain-commit recovery contract is reserved for the future Sink YOIP.

## Test Plan

Retain the current `CheckpointRecoveryTest`, `AlignedBarrierInputGateTest`, `ResultPartitionInputGateTest`, `SourceCoordinatorContractTest`, and `SinkWriterOperatorFailureTest` expectations. The following are required **for the stage that implements the relevant feature**, not claimed as existing tests:

1. **Current bounded behavior (A):** All records delivered; Source/Operator finish once per successful attempt; JDBC `flush(true)` on normal EOF; no forced final checkpoint. Failure/cancel does not perform normal final flush.
2. **Barrier vs EOF (A):** A valid barrier-before-EOF completes; a missing barrier due to EOF declines only the checkpoint, preserves ordinary data, and neither deadlocks nor publishes partial state.
3. **Checkpoint publication (A):** Snapshot/persistence failure preserves the last completed checkpoint. A published CP-100 followed by listener failure remains the newest valid recovery point; checkpoint publication remains successful and listener failure is separately observable, including when Source is already at EOF.
4. **Critical notification and restart (A):** Inject failures after Enumerator callback, after assignment-tracker callback, and on asynchronous Reader event delivery **after Source EOF**. No `anySourceFinished()` suppression, blind listener retry, or `FINISHED` race is allowed. Whole-job restart obeys configured attempts (including default zero) and validates CP-100.
5. **Final checkpoint completion (B):** With interval > 0 on a supported bounded Job, all downstream `EndOfData` consumption and final records are covered; logically finished Source sends control barriers, or downstream becomes the root after Source Task finishes. Physically finished Tasks are not awaited. A finished Sink Writer ACK does not call `snapshotState()` or repeat `flush(true)`. `interval=0, RESTORE_LATEST=true` does not require a final checkpoint. Transient pre-publication failure retries at most once within two `CHECKPOINTING_TIMEOUT` periods; exhausted/hard failures cannot report `FINISHED`, and CP publication alone never completes a Job.
6. **Finished vs running state (B):** A Task logically finished during a checkpoint is not automatically a fully finished operator; all parallel subtasks and final downstream output must be accounted for before marking it fully finished.
7. **Finished-on-restore (B):** Recovery from CP-100 skips business execution and final flush for fully finished operators, while still propagating required downstream termination/control events. A checkpoint made before finish does not skip that work.
8. **Partial completion (B):** Safe partial state resumes remaining work; unsupported state ownership/redistribution explicitly declines the checkpoint instead of silently discarding work.
9. **State format (B):** New finished-state format round-trips stable UIDs and subtask status, reads v1/v2 unchanged, and rejects corrupt, mismatched, or unsupported data.
10. **Future graph acceptance:** Once mixed bounded/unbounded topologies are supported, the unbounded branch continues checkpointing after its bounded predecessor has completed. This does not establish support today.

Run targeted unit/runtime tests and the existing Maven verification as appropriate to each implementation change; real-connector acceptance is required where JDBC/CDC behavior or persisted state changes. This documentation-only proposal supplies no new test evidence.

## Rejected Alternatives

- **Treat `END_OF_INPUT`, `EndOfData`, and `EndOfPartition` as one event forever:** Prevents checkpoints and control events after logical input completion.
- **Immediately add Flink's full event/finished-task class hierarchy:** Adds abstractions before supported callers and recovery tests exist.
- **Require Final Checkpoint for every current at-least-once JDBC Job:** Changes established behavior without a completed terminal-checkpoint protocol.
- **Mark Fully Finished when one Reader or Operator finishes:** Can lose in-flight final records, other subtasks' work, or pending external effects.
- **Skip all physical Tasks on finished restore:** Loses necessary downstream termination and checkpoint control events.
- **Revoke CP-100 or automatically restore CP-99 when its completion notification fails:** Confuses durable publication with best-effort post-publication callbacks.
- **Blindly retry completion listeners in place:** Existing Enumerator/assignment and async Source event callbacks do not universally guarantee repeatable side effects.
- **Merge transactional Sink commit, distributed failover, rescaling, or mixed-source execution into YOIP-1:** Those mechanisms need their own contracts and executable acceptance.

## Implementation note: Flink 1.20.3 source-level local storage alignment (PR #1820)

The current source migration replaces the previous `FileCheckpointStore` with
`LocalCompletedCheckpointStore` and relocates the Flink 1.20.3 local
`RecoverableWriter` / `LocalRecoverableFsDataOutputStream` and metadata-finalization
protocol into `yak-flow-runtime`. No Flink Runtime/Core JAR is introduced.

- `CheckpointMetadataSerializer` still owns YakFlow's versioned payload; the bytes are
  **not** compatible with native Flink `CheckpointMetadata`.
- Metadata is written into `checkpoints/chk-<id>/_metadata` through the
  Flink-derived `closeForCommit().commit()` lifecycle. A separately committed,
  immutable `completed/chk-<id>` marker records the checkpoint's external pointer.
  Only checkpoints with a completed marker are candidates for recovery; a marker
  referencing missing or corrupt metadata fails closed.
- The previous `checkpoint.bin` pointer format is not automatically upgraded.
  A directory with legacy state must be explicitly migrated/reinitialized only
  after resolving any outstanding external transactions.
- In contrast to Flink's highly available completed checkpoint store, the
  completed marker index is a small, local-only YakFlow adaptation. The filesystem
  controls the actual durability of rename and metadata persistence; the local
  RecoverableWriter does not guarantee distributed fault tolerance.
- The storage migration is independent of the pre-existing Flink CDC dependencies
  in `yak-ops-engine`. Full Engine dependency removal remains separate.

This implementation note supersedes the earlier `checkpoint.bin` storage-layout
example above but does not change YOIP-1's checkpoint ACK, publication,
completion-notification, or fail-closed recovery semantics.
