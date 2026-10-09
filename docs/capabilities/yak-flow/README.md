# YakFlow Capability

Status: Contracts + generic Core-based runtime only

当前分支的 `yak-ops-core` 保留 Source、Sink、Split、Enumerator、Reader、Operator、Configuration、Pipeline / JobClient 等通用协议。

`yak-flow-api` 暂时保留 Row、RowKind、Schema、Logical Type 值类型。旧版 Source / Sink / CheckpointState / Runtime Trace 协议以及旧 `LocalExecutionEngine` 执行路径已删除。

`yak-flow-runtime` 中 PR0～PR7 的通用 Core-based 执行框架仍在，但它没有可接入生产 Data Sync 的 JDBC / MySQL CDC Connector。Connector 模块保留 Maven 边界，不含离线或实时的具体实现。新 Runtime 的已有类不能视为真实数据库链路已完成验收。

模块边界由 [Core / Runtime Contract](core-runtime-contract.md) 和 [YakFlow Rules](../../../yak-flow/YAK_FLOW_RULES.md) 定义。

历史功能说明与人工验收保存在 [已发布版本](../../release/README.md) 及 [E2E 历史材料](../../e2e/data-sync/README.md) 中；不代表当前分支可直接运行这些场景。
