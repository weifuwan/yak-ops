# Core / Runtime Execution Contract

Status: Generic runtime foundation — no Data Sync connector integration

Scope: `yak-ops-core` 与 `yak-flow/yak-flow-runtime`。

## Core Ownership

- Source / SourceReader / SplitEnumerator、Sink / SinkWriter、Operator 与 Pipeline 等公共契约由 Core 定义。
- `Configuration` 与逻辑图定义不拥有正在运行的 Reader / Writer、线程或连接。
- `JobClient` / `PipelineExecutor` 提供统一提交接口，不承诺当前分支具备生产同步功能。

## Runtime Ownership

- 保留基于 Core 的 `CompiledJobPlan`、SourceCoordinator、StreamTask、OperatorChain、Channel、CheckpointCoordinator 等通用实现。
- 当前实现只覆盖受限单 JVM 线性图，不等价于 Flink 分布式运行时，也未完成完整 Connector / Data Sync 产品集成。
- 不得从通用 Runtime 存在推断离线 JDBC、MySQL CDC、真实数据库恢复或者 Exactly-once 已实现。
- 不将旧的 `LocalExecutionEngine` / `LocalExecution` 引回根包。
- 下一阶段再单独对齐 Flink 的 package、命名、TaskInfo / Graph 归属，本 PR 不搬迁通用框架类。

## Integration State

- JDBC 和 MySQL CDC Connector 模块现为接口预留模块，没有 Source、Reader、Enumerator 或 SinkWriter 实现。
- Data Sync 只保留业务接口，未向新 Runtime 提交作业。
- 旧测试和验收代码已按当前清理要求移除，暂不能声明完整编译、运行或真实数据库验收通过。

已发布版本相关资料保持历史原状；当前分支以本契约及实际代码为准。
