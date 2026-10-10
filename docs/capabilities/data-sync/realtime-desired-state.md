# Realtime Desired State + Auto Recovery

Status: Historical runtime design; only persisted desiredState behavior is currently active

**Current baseline:** The previous Business execution engine is removed. New YakFlow Runtime is not wired to product run/restore commands. The system keeps the persisted desired-state field and current Task/Instance cancel/unpublish semantics, but does not launch AUTO_RECOVERY executions on application restart. Historical recovery design below is not current executable behavior.

Scope: REALTIME 运行意图、启动协调、CDC 状态 identity 与恢复边界。

## State Ownership

发布状态回答是否允许创建新 Execution；desiredState 回答用户希望运行还是停止；Execution 状态回答某次运行的实际进展。三个状态不能相互替代。

`yak_ops_data_sync_task.desired_state` 保存 STOPPED / RUNNING。OFFLINE 固定 STOPPED，Cron 不复用该字段。发布规则见 [Task Lifecycle](task-lifecycle.md)，Execution / Attempt 见 [Retry Contract](execution-retry-attempt.md)。

## User Commands

| 命令或事件 | desiredState 语义 |
| --- | --- |
| 手工启动已发布 REALTIME Task | 置 RUNNING，同一业务命令创建 MANUAL Execution |
| 取消当前活动 REALTIME Execution | 置 STOPPED，再取消 Runtime / Execution / 活动 Attempt |
| 成功下线 Task | 置 STOPPED；已有活动 Execution 时仍按发布契约拒绝下线 |
| Execution 后续 FAILED / LOST | 不自动改成 STOPPED，运行失败不代表用户改变意图 |
| 对已终态 Execution 重复 Cancel | 只返回历史结果，不改变 Task desiredState |

当前没有独立的“只修改 desiredState”命令。无活动实例但意图仍为 RUNNING 时，不可借取消历史 FAILED / LOST 记录声称已停止意图；成功下线可清除意图。Realtime Task 列表因此把这种差异显示为“重新启动”，而不是“启动”或“已停止”；存在 PENDING / RUNNING / RETRY_WAITING 活动 Execution 时显示“停止”。接口限制不能被文档中的泛化 Stop 描述掩盖。

## Application Restart

```text
旧进程活动 Execution / Attempt → LOST
  → 扫描 PUBLISHED + REALTIME + desiredState=RUNNING 的 Task
  → 没有活动 Execution 且当前资源校验通过
  → 创建新 AUTO_RECOVERY Execution
```

活动集合见 [Execution Status](execution-retry-attempt.md#execution-status)。旧 LocalExecution 和根 Instance ID 不复活；新的根记录从 Attempt 1 开始，不跨进程续接旧 Attempt 序号。

历史设计曾要求 Boot 调用业务恢复入口。当前实现未接入新的 Runtime，不触发 AUTO_RECOVERY。启动仅按照现有 HistoryRecovery/Quartz 代码处理对应历史记录和已启用的 Cron 定义，不能把历史设计当作运行中的恢复功能。

这只发生在启动协调阶段，不提供常驻 watchdog。desired=RUNNING 且无活动执行应作为待处理差异展示，不当成“正常运行”或“用户已停止”。

## CDC Continuation

状态根目录为 `${yak.ops.home}/data/data-sync/realtime`；没有 yak.ops.home 时以工作目录为基准。identity 为 `{workspaceId}/{taskId}/v{definitionVersion}`，同一 identity 使用稳定 Debezium engine name 与状态目录。

Data Sync 管目录 identity，连接器私有地管理 offsets.dat 与 schema-history.dat。本地 serverId allocator 保证当前进程活动租约不冲突并在执行结束后释放，不是多节点协调。

同版本的新 Execution / Retry 复用既有状态范围；有效定义变化产生新版本与新状态范围。版本变化规则见 [Definition Version Contract](task-lifecycle.md#definition-version-contract)。不要仅因取消、失败、LOST 或产品版本升级就删除 CDC state。

续传依赖有效的连接器状态和仍可用的 Source 日志。首次无状态或新版本从 initial snapshot 开始；同版本但状态丢失、损坏或源端历史日志不可用，不能保证按原 offset 续传，也不能声称所有异常都会安全自动回退到全量。

容器替换后要续传，必须持久化 `${yak.ops.home}/data`；Docker 对应 `/opt/yak-ops/data`。仅持久化产品数据库不能替代 CDC state 文件。

Task definitionVersion 不包含被引用 Datasource 的连接修订。同 ID 原地改成另一个物理 Source，不会自动改变 state identity；当前没有 Datasource revision / fingerprint 或完整的引用变更保护，不承诺旧状态仍兼容。此风险需要独立能力设计，不能当作已解决。

## Retry Relationship

FAILED Attempt 的通用 Retry 留在原 Execution 内，root trigger 不变。启动自动恢复创建新 AUTO_RECOVERY 根记录，按当前已发布 Task 冻结输入；它不是旧 FAILED / LOST Execution 的 Retry。

v1.2 起，`RETRY_WAITING` 不再因为进程退出直接变成 LOST。它由 Durable Retry Recovery 保留原 Execution Root，并从持久化的 nextRetryTime 继续同一 Attempt Chain；OFFLINE 与 REALTIME 都遵循该规则。

PENDING / RUNNING 仍表示旧进程持有的 Runtime 已丢失，因此启动时标记 LOST。REALTIME desired-state 协调只在 Durable Retry Recovery 完成后运行；如果任务已经存在 RETRY_WAITING / PENDING / RUNNING Active Execution，则不会创建新的 AUTO_RECOVERY Execution。

## Delivery Semantics

继续采用 [YakFlow checkpoint 与 CDC 确认规则](../yak-flow/README.md#checkpoint-boundary)：Sink flush 完成后才能确认上游，持久化 offset 前仍可能重放，因此是 at-least-once。

不声明 exactly-once、通用 CheckpointState 跨进程恢复、旧 Runtime 对象复活、分布式 ownership / leader election / fencing 或连续故障自动拉起。

## Persistence and Compatibility

[v1.1.0 Release Migration](../../../yak-ops-dao/src/main/resources/db/migration/yak-ops/V2__v1_1_0.sql) 的 Realtime Desired State / Auto Recovery section 默认 desiredState=STOPPED；只有已发布 REALTIME Task 存在 PENDING / RUNNING / RETRY_WAITING 实例时回填 RUNNING。旧停止任务与 OFFLINE 保持 STOPPED。

同一 Release Migration 同时为 AUTO_RECOVERY 保留根触发类型的存储语义。Draft / Release Migration 生命周期和冻结规则见 [Flyway Rules](../../../yak-ops-dao/FLYWAY_RULES.md)。

## Code and Verification

入口：[DataSyncTaskServiceImpl](../../../yak-ops-business/yak-ops-business-data-sync/src/main/java/io/yak/ops/business/datasync/impl/DataSyncTaskServiceImpl.java) / [DataSyncInstanceServiceImpl](../../../yak-ops-business/yak-ops-business-data-sync/src/main/java/io/yak/ops/business/datasync/impl/DataSyncInstanceServiceImpl.java)。验证：[DataSyncRealtimeDesiredStateContractTest](../../../yak-ops-business/yak-ops-business-data-sync/src/test/java/io/yak/ops/business/datasync/impl/DataSyncRealtimeDesiredStateContractTest.java)、[DataSyncAutomationAcceptanceIT](../../../yak-ops-business/yak-ops-business-data-sync/src/test/java/io/yak/ops/business/datasync/impl/DataSyncAutomationAcceptanceIT.java) 和 [Automation E2E](../../e2e/data-sync/automation/README.md)。

状态 identity 测试不证明真实 Binlog 续传；还需 MySqlCdcIntegrationIT 的真实连接器验证和产品重启 E2E。执行结果放对应 CI / 版本证据，不写入本契约。
