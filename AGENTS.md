# Yak Ops Agent Context Router

Scope: Whole repository

本文件只路由任务，不复制业务契约、组件参数或开发进度。文档归属与更新规则由 [Engineering Context Model](docs/engineering-context-model.md) 定义。

## Context Loading

1. 确认目标分支和修改范围，文档、代码与验证使用同一基线。
2. 行为变更先定位对应 Capability / 组件 Contract；目录或依赖变更读取所属 Architecture；实现读取适用的全局及就近规则。
3. 再读取目标代码、直接依赖和相关验证入口。已知路径直接读取，不先遍历整个文档树。

导航链接不是必读清单。`Depends On` 用于定位适用约束，不要求递归加载所有链接；全局安全、兼容性和验证约束仍须遵守。

## Capability Context

业务能力从 [Docs Index](docs/README.md) 定位；只读当前任务涉及的专题。共享 UI 组件从 `yak-ops-ui/packages/yak-ui/docs/` 定位已有契约。

没有现成 Contract 时，先检查实现，再在合适的现有文档补充最小行为约束；没有独立职责就不新增文档。历史 PR、Release 材料和手工 E2E 只在任务涉及追溯、发布或验收时加载，不作为普通开发的默认前置上下文。

## Backend Context

Java 变更先读 `ARCHITECTURE.md` 和 `JAVA_RULES.md`，再按目标读取下列规则及其适用约束：

| 修改范围 | 规则入口 |
| --- | --- |
| 日志 | `LOGGING_RULES.md` |
| HTTP Controller | `CONTROLLER_RULES.md` |
| Platform | `yak-ops-platform/PLATFORM_RULES.md`；按 Security / Workspace / Preference 读取同目录的 `SECURITY_RULES.md` / `WORKSPACE_RULES.md` / `USER_PREFERENCE_RULES.md` |
| Business | `yak-ops-business/BUSINESS_RULES.md`；Task 定义读 `yak-ops-business/yak-ops-business-task/TASK_RULES.md`，数据源读 `yak-ops-business/yak-ops-business-datasource/DATASOURCE_RULES.md`，数据同步读 `yak-ops-business/yak-ops-business-data-sync/DATA_SYNC_RULES.md` |
| Common / DTO / VO | `yak-ops-common/COMMON_RULES.md`；共享 DTO / VO 另读同目录 `DTO_VO_RULES.md` |
| DAO / Entity / Migration | `yak-ops-dao/DAO_RULES.md`；按修改内容读取同目录 `ENTITY_RULES.md` / `FLYWAY_RULES.md` |
| Core / SPI | `yak-ops-core/CORE_RULES.md` / `yak-ops-spi/SPI_RULES.md` |
| Datasource Plugin | `yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN_RULES.md` |
| Task Plugin | `yak-ops-plugins/yak-ops-plugin-task/TASK_PLUGIN_RULES.md` |
| YakFlow | `yak-flow/YAK_FLOW_RULES.md` |

## Frontend Context

前端变更先读 `yak-ops-ui/ARCHITECTURE.md` 和 `yak-ops-ui/FRONTEND_RULES.md`。

当前 Web Root 是 `yak-ops-ui/apps/web`：业务在 `app/<domain>`，后端通信在 `service/<domain>`，共享 UI 在 `yak-ops-ui/packages/yak-ui`。不按旧迁移描述重建 `src / pages / shared`。

以下路径相对于 `yak-ops-ui/`，只读命中项：

| 修改范围 | 规则入口 |
| --- | --- |
| 产品页面、路由、Shell | `apps/web/APP_RULES.md` 和就近领域规则；表单另读 `apps/web/FORM_RULES.md` |
| API 调用与后端类型 | `SERVICE_RULES.md` |
| 共享 UI 组件 | `packages/yak-ui/UI_RULES.md` 和目标组件已有文档，不加载其他组件细节 |
| 依赖、Vite、TypeScript、格式、构建脚本 | `docs/tooling.md` |

## Documentation Context

文档变更先读 Engineering Context Model，再读目标权威正文及直接引用。发布任务从 `docs/release/README.md` 进入对应版本材料；手工数据同步验收从 `docs/e2e/data-sync/README.md` 进入对应场景。

遇到旧 Phase / PR 叙述或文档与实现冲突，按 Engineering Context Model 核对，不把历史阶段当成当前约束，也不擅自改代码迁就旧文档。

## Execution Rules

- 一个 PR 解决一个明确问题；优先修改已有实现、复用已有工具，不为对称性添加 Manager / Coordinator / Handler / Assembler / Adapter 或新层级。
- 不重新引入已删除领域、外部 yak-framework 依赖或未经任务要求的新框架。
- 代码变更维护相关现有测试和门禁，不用删除检查、跳过测试或降低约束让结果变绿。
- 验证命令使用所属规则和现有脚本；Java 遵循 `JAVA_RULES.md` 的 Spotless 要求，前端遵循 `FRONTEND_RULES.md` 的独立质量门禁。
- 文档变更校验引用、路由及机器读取字段；一次执行的结果放在 PR / CI 或对应版本证据，不追加进当前规则正文。
- 明确报告实际执行、未执行、失败和环境限制；不把计划、局部检查或条件跳过写成完整验收通过。
