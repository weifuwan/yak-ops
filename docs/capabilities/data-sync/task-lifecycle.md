# Data Sync Task Publication Lifecycle

Status: Active

Scope: OFFLINE / REALTIME Task 的发布、命令前置条件与定义版本。

## Goal

发布状态只回答“当前任务定义是否允许创建新 Execution”。执行状态见 [Execution Retry / Attempt](execution-retry-attempt.md)，实时运行意图见 [Realtime Desired State](realtime-desired-state.md)；三者不互相替代。

## Terminology

产品使用“上线 / 下线”，持久化使用 `PUBLISHED / UNPUBLISHED`，避免与同步类型 OFFLINE 混淆。状态枚举的存储值保持 `UNPUBLISHED=0`、`PUBLISHED=1`，Task 字段名仍为 `status`。

## Core State Machine

```text
Create → UNPUBLISHED v1
           ├─ 有效可执行定义变更 → UNPUBLISHED vN+1
           ├─ 元数据变更 / 无变化 → UNPUBLISHED vN
           └─ Publish → PUBLISHED vN
                          ├─ Run / Start → 新 Execution
                          └─ Unpublish → UNPUBLISHED vN
```

发布不创建 Execution，也不隐式启用 Schedule；运行不隐式发布。Execution 成功、失败、取消均不改变 Task 发布状态。

## Invariants

新任务从 UNPUBLISHED、definitionVersion=1 开始。`syncType` 不可修改；类型转换必须创建新任务。

当前 Update API 整体要求 UNPUBLISHED，包括只改 name / remark；不能把“元数据不增加版本”误解为“已发布时也可编辑”。Task 保存校验完整定义，不是部分草稿接口。

Run / Start 要求已发布且没有活动 Execution。活动集合由 [Execution Status](execution-retry-attempt.md#execution-status) 定义，包含 RETRY_WAITING。活动 Execution 阻止下线和删除；下线不是 Stop。前端状态展示不替代后端检查。

## Command Semantics

以下路径相对于 `/api/v1/data-sync`，实际 HTTP 声明由 Boot Controller 维护。

| 命令 | 前置条件及结果 |
| --- | --- |
| POST /tasks | 校验完整定义，创建 UNPUBLISHED v1；不发布、不运行 |
| PUT /tasks/{id} | 仅 UNPUBLISHED；重新校验资源与映射；按有效变化决定版本 |
| POST /tasks/{id}/publish | 仅 UNPUBLISHED；重新校验当前 Datasource、Catalog、类型及主键要求；版本不变 |
| POST /tasks/{id}/unpublish | 仅 PUBLISHED 且无活动 Execution；关闭 Schedule、清除实时运行意图；版本不变 |
| POST /tasks/{id}/run | 仅 PUBLISHED 且无活动 Execution；校验后创建冻结输入的新 Execution |
| DELETE /tasks/{id} | 仅 UNPUBLISHED 且无活动 Execution；清理 Schedule，保留运行历史 |

Stop / Cancel 属于 Execution 契约。成功下线对 Schedule 和 desiredState 的联动分别见 [Scheduler](scheduler.md) 与 [Realtime Desired State](realtime-desired-state.md)。

## Definition Version Contract

比较规范化后的可执行定义，而不是每次 PUT 都加一。后端 `executableDefinitionChanged` 比较：Source / Target 数据源 ID 与表范围、writeMode、`autoCreateTable`、历史任务级 **mapping**、对应 runtimeConfig，以及规范化后的 **retryPolicy**。

当前可编辑定义使用 Task 的单表 Source / Target 字段，不再接受显式 Mapping、多表 Route 或自动建表。历史 Execution 中的 Mapping/Route 快照按原样保留，版本比较仍确保旧字段收敛时递增。

name / remark、无实质变化的更新、发布 / 下线、运行 / 取消不增加版本。Schedule 单独持久化，修改 Cron / Time Zone 不调用 Task 版本比较。

产品版本与 Task definitionVersion 无关。Execution 创建后使用自己的 taskVersion 与 definitionSnapshot，Retry 不重新读取最新 Task 定义。

## Why V1 Does Not Need publishedVersion / draftVersion

当前只有一个 Task 行和一个 definitionVersion。已发布时不能编辑，因此无需另建 publishedVersion、draftVersion 或版本历史表。不支持同时运行旧发布版并编辑新草稿，也不提供定义回滚或审批流程。

## REALTIME Version and CDC State

CDC state 使用 `{workspaceId}/{taskId}/v{definitionVersion}`。同版本的新 Execution 复用该范围；任何有效可执行定义变更，包括 runtime tuning、retryPolicy 或 autoCreateTable，都会形成新版本和新的 CDC state 范围。仅改元数据或发布状态不改变该 identity。

完整续传条件和“引用的数据源原地改连接不会自动增加任务版本”的风险见 [CDC Continuation](realtime-desired-state.md#cdc-continuation)。不承诺跨物理 Source 变更仍能安全复用旧状态。

## Frontend Contract

数据集成编排定义、发布和 Task 范围的只读运行详情；运维中心编排 Run / Start / Stop、Schedule Runtime 和跨 Task 状态观察。Task Detail 可以读取该 Task 的 Execution / Attempt 历史，但不因此获得执行命令所有权。Task Editor 仍只负责定义。Save 与 Save & Publish 是已有 API 的显式组合，不新增自动运行路径；配置 Schedule 时顺序见 [Scheduler UI Ownership](scheduler.md#ui-ownership)。

历史 Execution 的事实来自持久化记录和冻结快照，不因 Task 下线而消失；删除 Task 仍保留运行历史供运维查询，但基于当前 Task 路由的详情页不承诺在 Task 删除后继续存在。具体页面布局、按钮和路由由前端 owner 维护，本契约不复制其视觉规则。

## Persistence

Task status 已属于现有 [V1 baseline](../../../yak-ops-dao/src/main/resources/db/migration/yak-ops/V1__baseline.sql)，不是待实施的新增列。数据库默认值 1 保留兼容语义，应用创建仍显式写入 UNPUBLISHED=0。后续变更遵循 [Flyway Rules](../../../yak-ops-dao/FLYWAY_RULES.md)，不能执行旧开发阶段的“新增状态列 / 回填所有任务”计划。

保留现有 Task ID、状态存储值及运行历史，不为整理文档改变 Schema 或引入物理外键。

## Non-goals

本契约不拥有 Scheduler 的计时、Retry 的 Attempt 或实时自动恢复算法；这些能力有独立现行契约，并非未实现。仍不提供自动 Stop-and-Unpublish、自动重新发布、CDC stateVersion、Datasource revision / fingerprint 或分布式发布协调。

## Canonical Definition Storage

Task ID、name、发布 status、definitionVersion 与 remark 现在由 [Task Definition](../task-definition.md) 通用表独立持有；单表来源/目标、Desired State 及运行参数仍在 DATA_SYNC 专属配置表。可执行配置版本递增时追加不可变历史快照，已存在的旧任务只回填当时的当前版本，不假造更早配置版本。

## Code and Verification

实现入口：[SyncDefinitionServiceImpl](../../../yak-ops-business/yak-ops-business-data-sync/src/main/java/io/yak/ops/business/datasync/impl/SyncDefinitionServiceImpl.java)。验证入口：[DataSyncTaskLifecycleContractTest](../../../yak-ops-business/yak-ops-business-data-sync/src/test/java/io/yak/ops/business/datasync/impl/DataSyncTaskLifecycleContractTest.java) 及 [Data Sync 验证导航](README.md#code-and-verification)。

重点检查命令前置条件、元数据与可执行定义的版本差异、历史快照保持以及 RETRY_WAITING 的活动状态语义。测试与手工验收的实际结果不写入当前规则正文。
