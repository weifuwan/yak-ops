# Yak Ops Docs

这是知识导航，不维护阶段进度或验证结果。已知目标时直接读取对应文档，不必通读索引中的所有入口。

## Engineering Context Model

文档归属、冲突处理和证据规则见 [Engineering Context Model](engineering-context-model.md)。任务到代码规则的路由见 [AGENTS.md](../AGENTS.md)。

## Current Knowledge Map

### 产品与执行能力

| 修改内容 | 文档入口 |
| --- | --- |
| 数据源资源与连接能力 | [Datasource](capabilities/datasource/README.md) |
| Task 插件类型注册与配置校验 | [Task Plugin Contract](capabilities/task-plugin.md) |
| 通用任务定义、版本与数据同步迁移 | [Task Definition Contract](capabilities/task-definition.md) |
| 离线 / 实时同步 | [Data Sync 总览](capabilities/data-sync/README.md)；按任务选读 [发布生命周期](capabilities/data-sync/task-lifecycle.md)、[调度](capabilities/data-sync/scheduler.md)、[执行与重试](capabilities/data-sync/execution-retry-attempt.md)、[实时期望状态与恢复](capabilities/data-sync/realtime-desired-state.md) |
| 执行引擎与连接器 | [YakFlow](capabilities/yak-flow/README.md)；[Core / Runtime 执行契约（提案）](capabilities/yak-flow/core-runtime-contract.md) |
| 工作空间与成员 | [Workspace](capabilities/workspace/README.md) |
| 用户收藏与使用偏好 | [User Preference](capabilities/user-preference/README.md) |
| 身份与支撑平台边界 | [Platform Rules](../yak-ops-platform/PLATFORM_RULES.md)、[Security Rules](../yak-ops-platform/SECURITY_RULES.md) |

### 前端与共享组件

[Frontend Architecture](../yak-ops-ui/ARCHITECTURE.md) 定位代码职责；[Frontend Rules](../yak-ops-ui/FRONTEND_RULES.md) 定义通用实现约束。

共享组件从 [Yak UI Rules](../yak-ops-ui/packages/yak-ui/UI_RULES.md) 进入 [组件文档目录](../yak-ops-ui/packages/yak-ui/docs/)，只加载目标组件契约。API 和工具链变更分别读取 [Service Rules](../yak-ops-ui/SERVICE_RULES.md) 与 [Frontend Tooling](../yak-ops-ui/docs/tooling.md)。

### 验收与发布

[Data Sync Manual E2E](e2e/data-sync/README.md) 提供可重复执行的产品验收步骤；[Version Management](release/README.md) 定位对应版本的范围、Readiness、Notes 和 Evidence。

发布状态只在版本材料中维护，本索引不复制版本号、冻结状态或 PASS 结果。普通开发不默认读取这些材料。

## Context Loading

选择本次任务对应的入口，再跟随必要专题和代码引用。索引描述用于定位，不代替专题正文；遇到旧阶段描述或相互矛盾的内容，按 Engineering Context Model 核对后处理。

## Engineering Rules

后端从 [Architecture](../ARCHITECTURE.md) 与 [Java Rules](../JAVA_RULES.md) 进入目标模块规则；前端使用上述前端入口。具体路径由 AGENTS.md 路由，本索引不重复维护全量模块规则清单。
