# Data Quality Architecture

本文件描述 Data Quality **当前长期架构**。它只记录现在有效的 package、角色、truth ownership 和调用边界，不记录迁移过程。

需求看 `REQUIREMENTS.md`，领域规则看 `DOMAIN.md`，依赖矩阵看 `DEPENDENCIES.md`，统一工程规范看仓库根目录 [`../../CODE_STYLE.md`](../../CODE_STYLE.md)。

## 1. Principles

1. **业务子系统优先。** package 本身表达架构，而不是统一堆进 service/impl。
2. **角色名表达职责。** Manager / Reader / Policy / Factory / Dispatcher / Worker / Recorder / Gateway / Adapter 不互相冒充。
3. **Command 与 Read Side 分开。** Reader/Projector 不修改 Monitor/Execution 生命周期。
4. **执行快照冻结。** Worker 消费 enqueue-time `QualityExecutionPlan`，不回读 current Monitor 改写本次执行。
5. **编排版本冻结。** Workflow 固定的是 Quality-owned immutable revision，不直接引用可变 Monitor 当前状态。
6. **外部能力停在 Gateway。** Datasource typed Catalog 通过 Quality-owned Gateway 进入。
7. **Persistence 走 Repository port。** Application role 不直接依赖 DAO/PO/MyBatis。
8. **Schedule 是投影。** MonitorSettings 是业务配置 truth，Yak Schedule 只负责触发。
9. **Task Catalog 是投影。** Quality Monitor revision 是内容 truth，Catalog 只保存发现元数据与 current revision pointer。
10. **架构规则可执行。** dependency/corridor/code-style tests 与文档共同维护。

## 2. Package Map

```text
io.yak.ops.business.quality
├── controller
│   └── v1/mapper          # HTTP inbound / DTO-VO mapping
├── asset                  # table registration + candidate/read policy
├── monitor                # monitor/rule/settings lifecycle
├── task                   # Workflow publication / revision resolution / executor adapter
├── execution              # admission / plan / dispatch / worker
├── alert                  # alert evidence recorder
├── schedule               # schedule lifecycle / framework bridge / callback
├── template               # built-in/custom template + folder
├── workspace              # report/workspace/log read side
├── gateway
│   └── datasource         # Quality-owned Datasource port + adapter
├── repository             # narrow persistence ports + adapters
├── dao                    # MyBatis persistence primitives
├── domain
│   └── execution          # immutable execution snapshot/definition
└── config                 # properties / condition / wiring
```

production 不创建 `service / common / helper / utils / base` 业务大桶。

## 3. Application Entry

Quality 当前**没有额外的通用 `@Service` facade**。

Controller 直接进入已经声明的专业 Application role：

```text
QualityTableAssetController
    -> QualityTableAssetManager
    -> QualityTableAssetReader
    -> QualityTableCandidateReader

QualityMonitorController
    -> QualityMonitorManager
    -> QualityMonitorReader

QualityExecutionController
    -> QualityExecutionManager
    -> QualityExecutionReader

CustomTemplateController
    -> CustomTemplateManager / Reader
    -> TemplateFolderManager / Reader

QualityWorkspaceController
    -> QualityWorkspaceReader

QualityExecutionWorkspaceController
    -> QualityExecutionWorkspaceReader
    -> QualityExecutionLogProjector
```

Workflow 不直接进入 Quality Controller，而是通过 Task Catalog + generic Job TaskExecutor 进入 `task` subsystem。

## 4. Asset Subsystem

```text
Controller
 -> QualityTableAssetManager / Reader / CandidateReader
        |
        +-> QualityTableTargetPolicy
        +-> QualityTableAssetRepository
        `-> QualityDataCatalogGateway
```

Manager 不把 Reader 当 Helper，Reader 也不拥有注册状态变化。

## 5. Monitor Subsystem

```text
QualityMonitorManager
    ├── QualityMonitorPolicy
    ├── QualityRulePolicy
    ├── QualityMonitorSettingsPolicy
    ├── QualityMonitorRepository
    ├── QualityExecutionRepository   # active-execution safety
    ├── QualityScheduleLifecycle
    `── QualityTaskPublisher

QualityMonitorReader
    `── QualityMonitorRepository
```

Manager 拥有 Monitor/Rule/Settings 的事务性生命周期。创建/更新可执行 Monitor 时同步 Quality task revision；停用、无可执行规则或删除时下线新的 Task Catalog discovery，但不删除历史 revision。

## 6. Workflow Task Subsystem

```text
current Monitor + Rules + Settings
    -> QualityTaskPublisher
    -> QualityExecutionPlanFactory.freeze
    -> immutable QualityExecutionDefinition
    -> QualityTaskRevisionRepository
    -> TaskCatalogService.publish(DATA_QUALITY, QUALITY)

Task Catalog revision lookup
    -> QualityTaskRevisionProvider
    -> Quality-owned immutable revision

Workflow runtime
    -> generic TaskExecutionGateway
    -> QualityTaskExecutor
    -> QualityExecutionManager.runWorkflowSnapshot
    -> existing Quality Execution subsystem
```

角色：

- `QualityTaskPublisher`：冻结 current Monitor 成 immutable revision，并维护 Catalog projection；
- `QualityTaskRevisionProvider`：把 Quality revision 暴露为 source-neutral `TaskSourceRevision`；
- `QualityTaskExecutor`：把 generic TaskExecutor start/status/cancel 映射到 Quality execution；
- `QualityTaskCatalogReconciler`：当前 Project 首次 discovery 时幂等修复旧 Monitor 的 Catalog projection；
- `QualityWorkflowExecutionRepository`：只承载 Workflow 特有的 snapshot admission、幂等键与取消状态，不扩大已有 execution repository contract。

Workflow 质量门禁语义：

```text
PASSED       -> SUCCEEDED
NOT_PASSED   -> FAILED
ERROR        -> FAILED
CANCELED     -> CANCELED
```

Task Catalog 不保存 `QualityExecutionDefinition` 内容，只保存 source/sourceRef/current revision pointer。历史 Workflow 因而继续解析自己固定的 revision，即使 current Monitor 后续被编辑或停用。

## 7. Execution Subsystem

高风险主路径保持显式：

```text
QualityExecutionManager
    -> admission
    -> insert WAITING Execution
    -> QualityExecutionPlanFactory
    -> immutable QualityExecutionPlan
    -> QualityExecutionDispatcher
         -> afterCommit
         -> qualityExecutionTaskExecutor
         -> QualityExecutionWorker
              -> QualitySqlCompiler
              -> QualityMetricEvaluator
              -> QualityDataCatalogGateway
              -> QualityExecutionRepository
              -> QualityMonitorRepository
              -> QualityAlertRecorder
```

Manual/Schedule 从 current Monitor 构造计划；Workflow 从 pinned `QualityExecutionDefinition` 构造计划。两条路径从 Plan 以后完全复用同一执行引擎。

Worker 对 Workflow cancel 做 best-effort 响应：未开始时取消阻止进入 RUNNING；运行中在规则边界检查取消状态，不伪造后续完成/告警结果。

## 8. Schedule Subsystem

```text
Monitor Manager
    -> QualityScheduleLifecycle
          -> QualityScheduleEngineBridge
          -> QualityMonitorRepository

Yak Schedule callback
    -> QualityScheduleHandler
          -> validate current Monitor/Settings
          -> QualityExecutionManager.runScheduled
          -> refresh runtime projection
```

`QualityScheduleReconciler` 负责从业务配置恢复 framework projection；它不拥有 Monitor 配置 truth。

## 9. Alert Subsystem

```text
QualityExecutionWorker
    -> QualityAlertRecorder
         -> QualityAlertRepository
```

Recorder 是 best-effort notification evidence boundary。Alert 写入失败只记录日志，不改写已经确认的 Execution result。

## 10. Template Subsystem

```text
QualityTemplateReader
CustomTemplateManager / Reader / Policy
TemplateFolderManager / Reader
        -> narrow template repositories
```

Template Policy 负责自定义 SQL 和参数 schema 的 validate/normalize；Template Manager/Folder Manager 负责生命周期；Reader 不写状态。

## 11. Workspace Read Side

```text
QualityWorkspaceReader
    -> QualityMonitorReader
    -> QualityWorkspaceRepository

QualityExecutionWorkspaceReader
    -> QualityExecutionWorkspaceRepository

QualityExecutionLogProjector
    -> persisted Execution / RuleExecution values only
```

Workspace 只做查询与 projection，不能出现 Monitor save/delete、Execution run 或 Schedule sync。

## 12. Datasource Boundary

Quality 与 Datasource 的唯一实现级连接点：

```text
QualityDataCatalogGateway
        ^
        |
DataSourceQualityCatalogAdapter
        |
        v
Datasource DataSourceCatalogReader
```

Asset / Execution 不允许为了方便直接调用 Datasource Reader、Repository、DAO、Plugin 或 Controller。

## 13. Persistence Boundary

```text
Application role
    -> Quality*Repository interface
    -> Repository Adapter
    -> Quality*Dao / Mapper
    -> PO / Mapper XML / MyBatis
```

Repository port 不暴露 DTO/VO/PO/MyBatis。

`yak_quality_monitor_revision` 保存 Workflow 可固定的 immutable execution definition；`yak_quality_execution.idempotency_key` 只用于外部编排重复提交去重。

## 14. Truth Ownership

```text
TableAsset                    = registered physical quality target
Monitor + Rules + Settings    = current monitor definition truth
QualityExecutionDefinition    = immutable Workflow revision content truth
QualityTaskRevision           = Workflow revision identity/checksum truth
Task Catalog                  = discovery/current-revision projection
QualityExecutionPlan          = immutable one-run execution truth
Execution                     = execution lifecycle/result evidence
RuleExecution                 = per-rule evidence
Monitor last-*                = execution projection
AlertEvent                    = notification evidence
Yak Schedule                  = trigger projection
Datasource Catalog            = physical metadata evidence
```

出现两个角色同时“决定”同一个 truth 时，先修 ownership，不要通过事件、静态工具或新的 Context 掩盖冲突。

## 15. Change Rule

新增或移动代码前依次回答：

1. 属于哪个 Quality subsystem？
2. 角色是什么？Manager/Reader/Policy/Worker/Gateway/Task Adapter 是否准确？
3. 它拥有哪个 truth，还是只读取/投影？
4. 新 import 是否符合 `DEPENDENCIES.md`？
5. 是否跨 Datasource？如果是，为什么 QualityDataCatalogGateway 不够？
6. 是否让 Worker 回读 current Monitor 改写 frozen plan？
7. 是否让 Workflow 直接绑定 mutable Monitor，而不是 revision？
8. 哪个 behavior test 与 architecture test 会保护这次改动？

答不清楚时不要创建新的 Helper/Common/ServiceImpl。
