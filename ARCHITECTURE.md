# Yak Ops Architecture

Status: Active

Scope: 当前模块职责、代码归属与依赖方向。文档归属遵循 [Engineering Context Model](docs/engineering-context-model.md)。

## Principle

**当前分支状态**：旧 Business execution 与 JDBC / CDC Connector 已移除；Data Sync 的前端、Controller、Service、DAO、Flyway 保留。YakFlow 具备单 JVM 的物理 JobGraph / ExecutionGraph 执行基础，但尚未连接新的 JDBC / CDC Connector，不能执行实际跨库同步。

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

拥有批流共享的 Source / Sink（兼容旧接口与带 WriterInitContext 的 SinkV2）、SinkWriter/StatefulSinkWriter、Collector / KeySelector、类型化 Configuration、Transformation、含 KeyGroup 最大并行度的只读 TaskInfo，以及 `PipelineExecutor` / `JobClient`。不包含 StreamGraph、Streaming Transformation、运行时 Operator、Channel、物理 Task、线程或 Checkpoint 执行器；不得反向依赖 Runtime。

### `yak-flow/yak-flow-api`

仅持有 JDK-only 的 YakRow、RowKind、YakTableSchema、YakDataType 等行/逻辑类型值对象；Source、Sink、Split 公共 API 归 Core。

### `yak-flow/yak-flow-runtime`

拥有 StreamGraph / StreamGraphGenerator、StreamingJobGraphGenerator、物理 JobGraph（JobVertex / JobEdge）、ExecutionGraph（ExecutionJobVertex / ExecutionVertex / Execution）、TaskDeployment、StreamTask、StreamOperator / OneInputStreamOperator / SinkWriterOperator、OperatorChain、SourceCoordinator、StreamTaskInput / RecordWriterOutput、ResultPartition / ResultSubpartition / InputGate、StreamPartitioner 与 AlignedCheckpointCoordinator。EmbeddedPipelineExecutor 仅负责编译和提交；EmbeddedJobClient 只提供查询、取消、结果和 Checkpoint 入口，运行状态由 ExecutionGraph 唯一管理。StreamTask 使用 TaskMailbox + MailboxProcessor：控制事件排队到所属 Task 线程，输入处理是可暂停的 MailboxDefaultAction。

当前只支持一个 Source → 零个或多个单输入 Operator → 一个 Sink 的严格线性图，保留单并行 FORWARD 内联链。跨 Task 的生产者使用 ResultPartition，消费者使用 InputGate；每个目标 Gate 的所有上游 Subpartition 共用一个有界缓存，支持 FORWARD / REBALANCE / KEYED（Murmur KeyGroup → Subtask）路由、背压、取消和失败清理。可恢复 Checkpoint 是单 JVM Source → Operator* → Sink 的 FIFO Barrier 对齐、Task Mailbox 状态 ACK 与磁盘原子提交；Operator/Writer 的命名状态使用 v2，纯 Source/Sink 无状态快照仍为兼容 v1。非 KEYED 拓扑指纹不变，KEYED 指纹绑定 KeyGroup 算法/最大并行度。仍只保证 at-least-once，不承诺 Exactly-once；多源、分叉、网络 Shuffle、动态扩缩容、远程 StateBackend 未实现。

Runtime 单向依赖 Core；内存 Execution / Attempt 与 Data Sync DAO 的产品实例身份不同。默认不自动重试；显式设置 execution.restart.max-attempts 时，仅允许从校验通过的持久化 Source / Operator / Sink Checkpoint 整 Job 重新装配，并为各 ExecutionVertex 创建递增 Attempt。Reader-only 热重启、分布式部署和 Slot/RPC 均未实现。旧 JDBC / CDC Connector 已删除，不能以历史跨库 E2E 声称当前能力。

新边界详见 [Core / Runtime Execution Contract](docs/capabilities/yak-flow/core-runtime-contract.md)。


### YakFlow Connector

旧 yak-flow-connector-jdbc 与 yak-flow-connector-cdc-mysql 已删除。仍用于产品 Schema/DDL 预览的 JDBC 类型映射与方言代码归 Datasource JDBC Plugin；新的执行 Connector 需要直接实现 Core Source / Sink。

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

只保留 DataSyncService / DataSyncServiceImpl、Task/Route/Definition、Schema 映射和预览、Schedule、历史 Execution / Attempt 查询及运维读模型。旧 business.datasync.execution 包已删除；历史运行态由 history.DataSyncHistoryRecovery 收口，新 Connector 未接入前手动运行和有效 Cron 触发均拒绝新建执行实例。产品持久化与运行时 ExecutionGraph 内存尝试互不混淆。详细见 [Data Sync Capability](docs/capabilities/data-sync/README.md)。

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

YakFlow Runtime（Graph / Operator / Execution / Checkpoint）→ Yak Ops Core（API / Configuration / Transformation）
YakFlow StreamGraph → JobGraph → ExecutionGraph → StreamTask
Datasource JDBC Schema → YakFlow Row / Type API
Boot Quartz → Data Sync Scheduler Contract
```

产品业务与执行机制分离：Quartz 不决定产品 Retry；YakFlow 不持久化产品 Execution；连接器私有状态不冒充通用 Runtime 恢复。当前运行范围为单节点，不声明分布式 ownership 或 exactly-once。

## Refactor Rule

按 [AGENTS.md](AGENTS.md) 定位适用契约与规则；结构变更先核对直接消费者，再修改最小必要范围。不因文件长或名称相似而新增模块、对称角色或抽象层。
