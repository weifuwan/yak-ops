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

## Configuration / Graph Guardrails

- `Configuration` 只表达类型化运行策略和构图所需默认值，不承载 JobID、Subtask、Attempt、Reader 或 Coordinator 的活动状态。
- `Transformation` 保存逻辑拓扑与可覆盖的算子属性；`StreamGraph` 保存解析后的节点属性，不持有本地 Task、线程、Gateway 或连接。
- 默认并行度与已解析的算子并行度必须区分。构图/提交时的配置快照需一致，不能让同一张图在运行时被另一份默认配置静默重解释。
- 稳定算子 UID 与单次构图 ID 必须分清。Source / Sink / Operator 的公共契约只暴露 Connector 和 Runtime 真正共同需要的语义，不加入产品任务身份。
- Runtime 的 TaskInfo、TaskEnvironment、CoordinatorContext 与物理装配属后续实现目标，按 [Core / Runtime Execution Contract](../docs/capabilities/yak-flow/core-runtime-contract.md) 的提案评审；不能因为希望缩短构造器就提前把这些本地实现放进 Core。

