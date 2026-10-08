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

暂时保留原有 Source / Sink、Row / Schema / Logical Type、Boundedness 与 CheckpointState 契约，供尚未迁移的代码使用；新引擎以 `yak-ops-core` 契约为目标，不在一条执行链路中混用新旧 Source API。过渡期间 API 保持 JDK-only。

### `yak-flow/yak-flow-runtime`

拥有本地执行实现：`CompiledJobPlan`（提交配置快照与图一致性校验）、`TaskInfo` / `TaskEnvironment`（子任务身份、配置快照与只读取消信号）、`StreamTask`、`SourceOperatorStreamTask`、`LocalPipelineExecutor`、`LocalJobClient`、`LocalJobRunner` 和 `source.coordinator`；后续逐步实现通用 Task 装配、Channel、全局 Checkpoint 等运行机制。依赖 `yak-ops-core`，并临时保留对旧 YakFlow API 的过渡依赖；不拥有产品 Task / Execution / Attempt 持久化、Cron 或业务重试策略。当前重构阶段不声明已具备完整运行闭环。 过渡期间保留现有 `io.yak.ops.flow.runtime.LocalExecutionEngine` / `LocalExecution` 入口，兼容 Data Sync 与 Connector 的旧 API 调用；旧执行路径与新 Core-based Runtime 独立验收，不以旧测试冒充新引擎的运行闭环。

新 Core / Runtime 的配置、执行图、Task/Coordinator 运行上下文与状态恢复的目标边界见 [Core / Runtime Execution Contract](docs/capabilities/yak-flow/core-runtime-contract.md)。该契约区分当前实现与拟引入的装配机制，不代表新 Runtime 已具备完整运行或恢复能力。

### `yak-flow/yak-flow-connector-jdbc`

拥有同步 SQL、逻辑类型映射与兼容性、split、Reader、SinkWriter 及数据库方言。复用 Datasource Plugin API 的规范化连接和 JDBC 运行时，不复制凭证配置或 Driver 装载机制。

### `yak-flow/yak-flow-connector-cdc-mysql`

拥有 MySQL CDC 到 YakRow 的转换、Debezium Engine 生命周期，以及连接器私有的 offsets / schema history。Debezium 和 Kafka Connect 类型不得进入 API / Runtime。

上述四个模块的执行、写入、checkpoint 与续传边界统一见 [YakFlow Capability](docs/capabilities/yak-flow/README.md)，包组织与实现约束见 [YakFlow Rules](yak-flow/YAK_FLOW_RULES.md)。

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

唯一稳定产品入口为 `DataSyncService`。拥有定义、发布、Schedule 业务记录、Execution / Attempt 生命周期、产品 Retry 和 REALTIME desired-state 协调；v1.2 起同时拥有产品级 Logical Table Schema Contract。

职责划分：

- `scheduler` 定义框架无关的 ScheduleEngine 与 Fire 回调；Quartz 实现在 Boot。
- `execution/planning` 将冻结快照、Catalog 与安全连接解析为内存执行计划。
- `execution/executor` 提交一次 Runtime 尝试并报告结果；`execution/lifecycle` 统一持久化状态、取消引用与启动 LOST 处理。
- `execution/realtime` 拥有 CDC state identity 和进程内 serverId 分配；连接器拥有状态文件内容。
- `schema` 拥有产品级 LogicalTable / LogicalColumn；复用 YakFlow Logical Type，但不把 Workspace、Comment、Schema Version 等产品元数据下沉到 Runtime。

通过 `DataSourceService` 读取 Catalog / 解析运行连接，不绕过该接口访问 Datasource DAO 或 Plugin Registry。运行计划可以间接持有凭证，但只能存在于内存，不能进入快照、响应或日志。

数据同步扩展到多表、增量和长期运行时，产品级 ownership 仍留在 Data Sync：

- Task 级共享策略与每张表的稳定 Route identity 由 Data Sync 拥有。
- 一次 Task 运行的 Root Execution、单表 Table Execution 与 Attempt 由 Data Sync 负责持久化和聚合；YakFlow 不成为产品级多任务调度器。
- OFFLINE Incremental 的 confirmed cursor / watermark、Schema baseline / diff、Recovery budget 与 Task Health 都是产品事实，不下沉到 YakFlow。
- YakFlow 继续接收单条已解析的 Source → Sink 执行计划并负责数据平面；连接器私有 checkpoint 不能替代产品级 Route 状态。
- REALTIME periodic reconciliation 属于 Data Sync lifecycle；Boot 可以负责调度 / 装配入口，但不能直接查询 DAO 后自行创建 Execution。

v1.3 的具体范围、兼容要求与 Non-Goals 由 [v1.3.0 Release Contract](docs/release/v1.3.0.md) 冻结；在对应实现 PR 合并前，这些规划能力不计入当前已实现 Capability。

详细行为由 [Data Sync Capability](docs/capabilities/data-sync/README.md) 及其专题定义，实现约束见 [Data Sync Rules](yak-ops-business/yak-ops-business-data-sync/DATA_SYNC_RULES.md)。

### `yak-ops-plugins/yak-ops-plugin-datasource`

拥有 Provider SPI、运行时 Descriptor、连接解析、Driver 选择、连接测试和 Catalog。Provider 自己定义 canonical type / aliases；Common 与 Service 不维护数据库类型大全或 Provider switch。

Descriptor 是运行时元信息，不是前端动态表单协议。内置 Provider 由聚合模块装配，扩展边界见 [Plugin Rules](yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN_RULES.md)。

### `yak-ops-boot`

拥有所有 HTTP Controller / ControllerAdvice、健康入口、全局运行配置与最终应用装配。Controller 位于 `io.yak.ops.boot.controller`，只依赖稳定 Service 和共享 DTO / VO。

GlobalExceptionHandler 统一映射 BusinessException 与 ErrorCode；领域模块不创建第二套 HTTP 异常出口。运行时保持单一应用 DataSource、默认事务管理器、MyBatis-Plus 会话工厂与拦截器链、OpenAPI 文档。优先使用 Spring Boot / Starter 自动配置，不手工重建已提供的基础设施 Bean。

QuartzScheduleEngine、Quartz JobFactory、Job 以及启动恢复装配在 Boot。Job 只把稳定 ID 和触发时间交回业务；不直接查询 Repository 或提交 YakFlow。DAO 继续拥有 Schema，Quartz 不拥有业务状态。

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
UI → HTTP → Boot
             ├─ Platform → DAO / Common
             ├─ DataSourceService → DAO / Datasource Plugin API
             └─ DataSyncService → DAO / DataSourceService / YakFlow

YakFlow Runtime → Yak Ops Core / YakFlow API（过渡）
YakFlow Connectors → Yak Ops Core（目标）/ YakFlow API / Datasource connection runtime（过渡）
Boot Quartz → Data Sync Scheduler Contract
```

产品业务与执行机制分离：Quartz 不决定产品 Retry；YakFlow 不持久化产品 Execution；连接器私有状态不冒充通用 Runtime 恢复。当前运行范围为单节点，不声明分布式 ownership 或 exactly-once。

## Refactor Rule

按 [AGENTS.md](AGENTS.md) 定位适用契约与规则；结构变更先核对直接消费者，再修改最小必要范围。不因文件长或名称相似而新增模块、对称角色或抽象层。
