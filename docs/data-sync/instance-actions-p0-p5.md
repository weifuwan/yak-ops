# Data Sync Instance Actions and Recovery

## Instance action policy

| Instance type and state | Available actions |
| --- | --- |
| Realtime, running | Stop (generate and persist a Savepoint before stopping) |
| Realtime, stopped / killed | Resume from the latest usable recovery point, and rerun from the beginning |
| Realtime, failed / waiting for fault tolerance | Recover failure from the latest usable recovery point, and rerun from the beginning |
| Offline, stopped / paused | Resume the workflow instance, and rerun it |
| Offline, failed | Recover failed workflow tasks, and rerun the workflow |
| Any type, workflow failover in progress | No manual action |

Realtime recovery actions are exposed only when a completed Checkpoint or Savepoint with a non-empty restore path is available. Without one, rerun remains available. Rerun never passes a restore path and supersedes all available recovery points for the source instance.

Instance actions lock both the task-instance row and its task-definition row. Ordinary manual DataSync workflow starts use the same task-definition lock, preventing a concurrent start from racing with resume/recover/rerun.

## Checkpoint collection

The DataSync pipeline builder supplies these defaults under `pipeline.flink-conf`, while preserving user overrides:

- `execution.checkpointing.interval: 30s`
- `execution.checkpointing.num-retained: 3`
- `execution.checkpointing.externalized-checkpoint-retention: RETAIN_ON_CANCELLATION`
- `state.checkpoints.dir`: local default described below

The Worker polls the Flink REST endpoint `/jobs/{jobId}/checkpoints` and reports the latest completed checkpoint with a usable `external_path`. The Master persists checkpoint metadata idempotently and retains at most the latest three completed Checkpoints for each execution. A STOP action separately creates a Savepoint and persists its intent before invoking the Worker RPC.

## Storage configuration

The local integrated/MiniCluster default is:

`<user.dir>/data/yak-ops/data-sync/checkpoints`

Override it with either:

- JVM property: `yak.ops.data-sync.checkpoint-directory`
- Environment variable: `YAK_OPS_DATA_SYNC_CHECKPOINT_DIRECTORY`

The STOP Savepoint directory can be configured with:

- JVM property: `yak.ops.data-sync.savepoint-directory`
- Environment variable: `YAK_OPS_DATA_SYNC_SAVEPOINT_DIRECTORY`

The default local `file:` paths are suitable only when the same Worker host and filesystem remain available. For YARN or multi-Worker deployments, configure a durable shared filesystem path (for example, HDFS or an object-store-backed Flink filesystem) that every execution node can access. A local path on one Worker is not portable to another Worker.

## Recovery and lineage

Resume and recover-failure create a new workflow instance using the source instance's pinned workflow-definition version. The new execution receives both the restore path and recovery-point ID. The recovery-point record is marked as used by the new workflow instance; the new Worker's first accepted report links the new task-instance ID back to that recovery point. The new execution then reports its own Checkpoints under its own task-instance ID.

Rerun creates a new execution without a restore path and marks all available recovery points of the source instance as superseded.

## Database upgrade

Fresh initialization and the base patch scripts create the multi-point recovery table. If a database has already applied the earlier single-Savepoint schema from this feature branch, execute the matching `data-sync-recovery-point-v2_*.sql` migration once before deploying this revision.

## Regression coverage

The implementation tests cover the action policy, task-definition locking, Savepoint persistence/failure, Flink REST checkpoint parsing, bounded Checkpoint retention defaults, checkpoint report persistence, restore-path propagation, and recovery-point lineage linking.
