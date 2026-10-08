# Core Rules

Scope:
- `yak-ops-core/**`

Status:
- Active

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`

## Current Fact

`yak-ops-core` 维护批流共用的 Source / Sink / Operator API、配置、Transformation / StreamGraph 逻辑模型，以及 `PipelineExecutor` / `JobClient` 契约。

`LocalPipelineExecutor`、`LocalJobClient`、`LocalJobRunner` 和 `SourceCoordinator` 等本地运行实现归 `yak-flow-runtime`，不再放在 Core。

## Entry Rule

新能力只有同时满足以下条件之一时才考虑进入 Core：

- 有独立生命周期。
- 有稳定 Runtime Contract。
- 有独立状态机或并发资源边界。
- 明显独立于 Datasource 持久化和 HTTP。
- 未来存在真实多 owner 复用，而不是假设复用。

进入 Core 前必须先更新 `ARCHITECTURE.md` 并 Review ownership。

## Must Not

- 因为 Datasource 类变长就把代码搬进 Core。
- 因为“看起来通用”就提前抽 Core。
- 把 Repository / Mapper / HTTP / Controller 放进 Core。
- 把 Datasource 业务事实变成 Core runtime state。
- 重建已经删除的 Notification / Task / Alert runtime。

## Boundary

Core 拥有跨 Connector / Runtime 复用的稳定接口、配置与逻辑图模型；不创建运行线程，不拥有本地 Job 状态、Reader 调度或 Checkpoint 执行过程。

**代码始终留在真实 owner。** 运行期职责归 Runtime，数据库特定逻辑归 Connector，业务持久化和任务状态归 Business。
