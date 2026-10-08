# YakFlow Rules

Status: Active

Scope: `yak-flow/**`。

遵循 [Architecture](../ARCHITECTURE.md)、[Java Rules](../JAVA_RULES.md)。数据平面、类型、split、写入、checkpoint 和续传的权威行为定义在 [YakFlow Capability](../docs/capabilities/yak-flow/README.md)，不在此复制参数和阶段说明。

## Product Boundary

YakFlow 不拥有 Task / Execution / Attempt 数据库、产品发布状态、Cron 或 Retry Policy。不能用连接器计数或本地 checkpoint 冒充产品持久化、事务提交证明或跨进程恢复。

## API Contract

API 保持 JDK-only：用 Boundedness 表达生命周期，用 YakRow / RowKind 表达数据变化，用不透明 CheckpointState 隔离连接器细节。不创建 BatchSource / CdcSource 两套协议或额外 JobMode。

Spring、JDBC、Debezium、Kafka Connect 和产品 DTO 不进入 API。数据库原生类型转换归 Connector，通用逻辑类型及参数归 API；不要为尚不存在的 Transform / DAG 扩张契约。

Runtime Trace 只在 API 定义最小 Event / Listener 协议，JDBC SQL、Split 范围、Batch 等具体事件结构留在 Connector。Trace 是 best-effort 诊断旁路：不得持久化产品状态、不得记录业务行值 / Sink bind 参数，也不得因为 Listener 失败改变数据流结果。

## Local Execution Engine

只依赖 API，线程和 Channel 保持有界。Reader 并行度不决定 split 数量，Enumerator 分配与 Sink 写入仍串行。具体范围和 checkpoint 限制以 Capability 为准。

取消 / 失败时释放或中断阻塞工作；连续 Reader.poll 不得永久阻塞。checkpoint 顺序必须是 Source capture → barrier → Sink flush → completion，不能为吞吐提前确认上游。

不把 Connector 特例写入 Runtime；不使用 Java 序列化强行持久化 opaque CheckpointState；不新增远程 RPC、Worker Registry 或分布式资源管理层。

## Core-Based Runtime Target Boundary

本节约束以 `yak-ops-core` 为接口的新 Runtime 迁移，**不表示已完成装配与验收**。目标契约见 [Core / Runtime Execution Contract](../docs/capabilities/yak-flow/core-runtime-contract.md)；上面的 Local Execution Engine 和 Connector 规则仍约束既有 YakFlow API 路径。

- `SourceCoordinator` 使用协调侧 Context 管理并行度、Reader 注册、Split 分配及事件发送；`SourceOperator` 使用 Task 运行上下文创建 `SourceReaderContext`，不由构造器各自维护可能冲突的运行参数。
- Coordinator 的事件循环与 Task 的 Mailbox 各自串行处理所属状态；事件投递、事件处理、Split 消费和 Checkpoint 成功必须分别定义确认语义。
- `CompiledJobPlan` 在提交时冻结配置，校验已生成的 StreamGraph 并确定运行模式；Runner 只接收该计划，不能再以另一份默认配置解释节点并行度。
- 物理 Task/Channel 装配归 Runtime，默认配置不代替已解析执行图属性；不提前引入远程 RPC、分布式调度器或万能 Environment。
- 只有完整状态持久化并获得下游确认后才能通知 Checkpoint 完成；单独的 SourceCoordinator 快照不能宣称可恢复的完整 Job Checkpoint。
- 新旧 Source / Sink API 迁移需要独立的适配与验收，不能直接以旧 LocalExecution 的测试结果作为新 Runtime 的通过证据。


## Package Organization

按实际职责聚合，避免按文件数量拆包。Connector 默认浅层：source / sink / dialect / debezium 等包有真实类族才创建，不建立单类包或空未来包。

Source / Split / Enumerator 保持内聚；MySQL `source` 管 YakFlow 生命周期，`debezium` 隔离 Engine / SourceRecord / RecordCommitter 等实现。不能用 util / helper / manager / common 大桶回避归属。

单元测试镜像对应职责；真实外部系统验收放 integration 包。

## JDBC Batch Connector

复用 Datasource Plugin API 的规范化 Connection / TablePath 与 Driver / SSH runtime，不复制 host / port / password 模型。数据库标识符由方言引用，禁止拼接任意用户 SQL。

列顺序从声明 Schema 贯穿 Reader、YakRow 和 Writer。JdbcSchemaMapper 负责 Catalog → 逻辑类型；JdbcSchemaCompatibility 只接收逻辑列，Data Sync Business 不得另写 JDBC 类型分类。

target pre-write 与逐行 write 保持分离，遵循 [JDBC Batch Contract](../docs/capabilities/yak-flow/README.md#jdbc-batch-connector)。按事务批次提交，失败回滚未提交内容；不得把 OVERWRITE 宣称为原子替换或在 TRUNCATE 失败时偷偷执行 DELETE。

JDBC `dialect` 同时拥有目标 Native Type 与 CREATE TABLE / Comment DDL 规划，复用同一套 identifier / table path / literal escaping 规则：

- `JdbcNativeType` 表达原生类型、非阻塞 warning 和是否可直接作为主键。
- `JdbcDialect#createTableSql` 保留单条 CREATE TABLE Contract；`createTablePlan` 生成按顺序执行的完整建表计划，第一条固定为 CREATE TABLE，后续可包含受控 Comment DDL。
- 不支持的语义必须抛出明确 UnsupportedOperationException，由产品 Planner 转成 blocking diagnostic；禁止静默缩窄类型。
- 目标类型映射可以安全放宽容量，但必须对未知容量 / 精度给 warning。
- Table / Column Comment 属于当前建表元数据 Contract：MySQL 可内联，PostgreSQL / Oracle 可使用 COMMENT ON；INDEX / foreign key / schema-evolution DDL 仍不在当前基础方言 Contract 内。
- `JdbcTargetTableProvisioner` 是当前唯一目标建表 DDL 执行入口；常规入口只接受受控 TablePath + YakTableSchema + Comment 元数据，由 JdbcDialect 生成计划，不接受用户 SQL 字符串。
- Provisioner 只负责执行已规划 CREATE TABLE / Comment statements；是否允许自动创建、表存在性、Schema Compatibility 和并发创建后的 re-introspection 归 Data Sync。
- Connector 禁止通过 Provisioner 执行 DROP / ALTER / INDEX 或其它任意用户 DDL；Comment 只允许作为目标新表 DDL Plan 的受控元数据语句执行。

## MySQL CDC Connector

Debezium 版本归 BOM；所有 Debezium / Kafka Connect 类型、offset 与 schema-history 内容留在 Connector。连接端点和 SSH 复用 Datasource runtime，不新增强制外部 Kafka 服务。

仅在下游 barrier flush 完成后的 notifyCheckpointComplete 确认记录；未完成持久化的 offset 可能重放，不能宣称 exactly-once。状态目录由调用方提供，不能自行持久化产品 Task 或定义第二套执行身份。

## Integration Test Boundary

协议验证使用测试自有的隔离容器，不访问开发者或共享数据库。MySQL CDC 必须启用真实复制协议；等待必须有上限，不能以连接成功代替生命周期与结果断言。

保留转换、类型与配置的单元覆盖；真实数据库验收类使用 `*IT`。执行命令、触发路径和完整扫描入口由 [Backend Acceptance](../.github/workflows/backend-acceptance.yml) 维护，普通 verify 与专项验收不能互相冒充。

Capability 的 [Verification](../docs/capabilities/yak-flow/README.md#verification) 定义必须验证的行为。文档不保存某次 CI 绿色结果；没有实际运行的检查不能记为通过。
