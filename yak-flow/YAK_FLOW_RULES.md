# YakFlow Rules

Status: Active

Scope: `yak-flow/**`。

## Boundary

- `yak-ops-core` 是下一轮 Source / Sink / Operator 的公共协议权威。
- `yak-flow-api` 只保留 Row / Logical Type 等必要的数据模型，不维护第二套 Source / Sink。
- `yak-flow-runtime` 只保留 Core-based 通用运行框架；旧根包 `LocalExecutionEngine`、`LocalExecution`、Channel / Checkpoint 旧链路不得重新添加。
- JDBC、MySQL CDC 模块当前不提供具体同步实现；不以空模块或泛型框架宣称已支持真实数据库同步。
- Data Sync 的 Task / Execution / Attempt 与业务调度不进入 Runtime。

## Implementation Rules

具体实现留到后续 PR。保持 Core → Runtime 单向依赖和跨模块边界，不引入 Spring / JDBC / Debezium 类型到 Core。

类名、package 和职责后续以 Flink 的实际所属模块为参照，但不把尚不存在的分布式机制伪装成已实现能力。

本轮按要求移除旧测试，不以无测试或空验收任务的成功结果宣称运行已验证。历史版本的 E2E 与发布证据保持不变。
