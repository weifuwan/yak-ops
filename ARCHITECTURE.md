# Yak Ops Architecture

Status: Active

Scope: 当前模块职责、代码归属与依赖方向。文档归属遵循 [Engineering Context Model](docs/engineering-context-model.md)。

## Principle

Datasource 管资源与连接，Data Sync 管同步任务和运行语义，YakFlow 管执行机制。Platform 提供身份、工作空间和用户偏好。能力边界不等同于页面菜单或 Maven 模块数量。

HTTP 入口与最终应用装配归 Boot。能力模块不依赖 Boot，不复制 HTTP、持久化或连接模型。行为细节由各能力 Contract 定义，本文件不维护第二套状态机或开发进度。

## Current Modules

### `yak-ops-common`

拥有跨模块共享 DTO / VO、Result / ErrorCode / PageData、请求 WorkspaceContext、BusinessException 及通用工具。领域私有异常和内部模型仍归所属能力；共享 HTTP 类型不能泄漏 DAO 或具体插件类型。

### `yak-ops-platform`

同一物理模块内保留 Security/User、Workspace、User Preference 的独立包和 Service 边界。Security 负责认证策略、HttpSession 登录态及认证拦截器实现；拦截器注册与 HTTP 暴露由 Boot 完成。

Security 使用 `io.yak.ops.security` 命名空间，领域错误与内部用户模型留在该边界。共享 HTTP DTO / VO 在 Common，用户 Entity / Repository 在 DAO；持久化枚举 `UserStatus` 由 Common 提供。面向 Boot 的身份与用户入口是 `LoginService`、`UserService`，用户管理不再复制一套具体服务。

实现约束见 [Platform Rules](yak-ops-platform/PLATFORM_RULES.md) 和 [Security Rules](yak-ops-platform/SECURITY_RULES.md)。

### `yak-ops-dao`

拥有全部 Entity / Mapper / Repository、Mapper XML、Repository 基础设施、统一 Flyway 配置和迁移 SQL。业务实现可以内部使用 DAO 类型，Boot 不直接消费它们。

Schema 位于 `yak-ops-dao/src/main/resources/db/migration/yak-ops`。迁移冻结与向前演进遵循 [Flyway Rules](yak-ops-dao/FLYWAY_RULES.md)；最终 DataSource / MyBatis / 事务运行时由 Boot 装配。

### `yak-ops-spi`

保留的最小扩展边界；不为尚不存在的能力预建接口。

### `yak-ops-core`

拥有新的批流共用 Source / Sink / Operator API、类型化 Configuration、Transformation / StreamGraph 以及 `PipelineExecutor` / `JobClient` 稳定契约。只保留公共协议和逻辑拓扑，不创建本地运行线程，也不持有运行中 Job、Reader 或 Connector 连接。

### `yak-flow/yak-flow-api`

暂时保留 Row、Schema、Logical Type 等值类型。旧 Source / Sink / CheckpointState 接口已删除；诊断 Trace 事件契约已恢复；新的统一 Source / Sink API 以 `yak-ops-core` 为准。

### `yak-flow/yak-flow-runtime`

保留以 Core API 为基础的新通用单 JVM 执行框架，包括 StreamTask、SourceCoordinator、Channel、CheckpointCoordinator 与 PipelineExecutor 的实现。尚无生产 Data Sync Connector 集成或业务执行入口，不宣称离线、实时或多表链路可运行。根包的旧 LocalExecutionEngine / LocalExecution / Checkpoint / Channel 链路已删除。新 Runtime 的 Flink 包组织另行审查，不在本轮搬类。

### `yak-flow/yak-flow-connector-jdbc`

保留 JDBC Schema / Catalog / Dialect / DDL 预览和 Trace 辅助类型；旧 JDBC Source / Reader / Sink / Writer 数据执行实现已移除。

### `yak-flow/yak-flow-connector-cdc-mysql`

保留 Maven 模块边界，不含旧 MySQL CDC Source、Debezium 及其恢复实现。

模块的当前能力边界见 [YakFlow Capability](docs/capabilities/yak-flow/README.md)，实现规范见 [YakFlow Rules](yak-flow/YAK_FLOW_RULES.md)。

### `yak-ops-business`

拥有产品 Service Layer：稳定接口面向 Boot，`impl` 负责事务、校验以及 DAO / Plugin 编排。默认命名与能力显式选用 Service 命名的规则见 [Business Rules](yak-ops-business/BUSINESS_RULES.md)，同一能力不并存 Business / Service 两套入口。

### `yak-ops-platform` / Workspace

`WorkspaceService` 拥有工作空间与成员关系；它不是 Security 角色模型。Boot 校验 `X-Workspace-Id` 的成员关系后绑定可信 WorkspaceContext；需要工作空间的能力显式要求该上下文。

行为见 [Workspace](docs/capabilities/workspace/README.md)。

### `yak-ops-platform` / User Preference

`UserPreferenceService` 拥有用户级收藏与使用信号；不按 Workspace 隔离，也不保存菜单标签、路由、图标等产品注册信息。跨设备偏好的持久化来源是服务端，不是浏览器缓存。

行为见 [User Preference](docs/capabilities/user-preference/README.md)。

### `yak-ops-business/yak-ops-business-datasource`

唯一稳定入口为 `DataSourceService`。拥有 Workspace 内资源管理、连接测试、只读 Catalog、内部 Plugin 路由和安全连接解析。Registry / SecretCodec 是内部机制，不作为 Boot 的第二套入口。

通过 Plugin API 使用 Provider，不直接耦合具体 JDBC 实现；不提供任意 SQL 执行、SQL 审计或重复的 Domain / Gateway 层。行为和实现约束分别见 [Datasource](docs/capabilities/datasource/README.md)、[Datasource Rules](yak-ops-business/yak-ops-business-datasource/DATASOURCE_RULES.md)。

### `yak-ops-business/yak-ops-business-data-sync`

恢复 `DataSyncServiceImpl`、Task / Route / Schedule 定义与 Schema 预览、历史运维读取及所需 Lifecycle / Trace 类型。DAO 的 Entity / Mapper / Repository 和所有既有 Flyway Migration 保留，不回滚数据库结构。

旧数据执行器依然删除。运行任务或启用 Cron 会明确返回 `ENGINE_UNAVAILABLE`，不创建无法执行的实例；自动恢复和调度触发不产生新任务实例。Controller、前端任务和运维页面已恢复，详见 [Data Sync Capability](docs/capabilities/data-sync/README.md) 和 [Data Sync Rules](yak-ops-business/yak-ops-business-data-sync/DATA_SYNC_RULES.md)。

### `yak-ops-plugins/yak-ops-plugin-datasource`

拥有 Provider SPI、运行时 Descriptor、连接解析、Driver 选择、连接测试和 Catalog。Provider 自己定义 canonical type / aliases；Common 与 Service 不维护数据库类型大全或 Provider switch。

Descriptor 是运行时元信息，不是前端动态表单协议。内置 Provider 由聚合模块装配，扩展边界见 [Plugin Rules](yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN_RULES.md)。

### `yak-ops-boot`

拥有所有 HTTP Controller / ControllerAdvice、健康入口、全局运行配置与最终应用装配。Controller 位于 `io.yak.ops.boot.controller`，只依赖稳定 Service 和共享 DTO / VO。

GlobalExceptionHandler 统一映射 BusinessException 与 ErrorCode；领域模块不创建第二套 HTTP 异常出口。运行时保持单一应用 DataSource、默认事务管理器、MyBatis-Plus 会话工厂与拦截器链、OpenAPI 文档。优先使用 Spring Boot / Starter 自动配置，不手工重建已提供的基础设施 Bean。

保留 Data Sync Controller 与 Quartz 调度配置、Cron 预览等非执行能力；旧的引擎执行恢复启动器保持移除。

### `yak-ops-ui`

拥有浏览器产品入口；具体目录、Shell 与页面职责见 [Frontend Architecture](yak-ops-ui/ARCHITECTURE.md)，不在后端架构中复制前端能力清单或视觉参数。

### `yak-ops-dist`

拥有分发包与发布装配。发布过程和版本证据见 [Version Management](docs/release/README.md)，不进入普通能力实现的默认上下文。

### `yak-ops-bom`

拥有统一依赖版本；版本值从 BOM 读取，不在能力正文重复维护。

## External Framework Boundary

Yak Ops 不依赖外部 `yak-framework`。现有 Common 与 Platform 能力由本仓库拥有，不重新引入已删除的框架层。

## Dependency Direction

```text
UI → HTTP → Boot → DataSyncService / Datasource / Platform
                          ├─ Common / DAO
                          └─ Datasource Plugin / JDBC Metadata

DataSyncService → Task / Schedule / Instance Repository
YakFlow Runtime → Yak Ops Core
JDBC Metadata / Dialect → YakFlow API Row + Datasource Plugin
```

当前 Data Sync 有业务管理入口，但没有可用的数据同步执行引擎。任务配置和历史查询的恢复不代表新 Runtime 已完成业务接入。

## Refactor Rule

按 [AGENTS.md](AGENTS.md) 定位适用契约与规则；结构变更先核对直接消费者，再修改最小必要范围。不因文件长或名称相似而新增模块、对称角色或抽象层。
