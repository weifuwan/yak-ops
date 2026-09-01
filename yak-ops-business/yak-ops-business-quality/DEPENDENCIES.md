# Data Quality Dependencies

本文件定义 Data Quality package 的**允许依赖方向、跨子系统 corridor 和跨模块边界**。原则：**显式、窄、无环**。

架构角色看 `ARCHITECTURE.md`；统一工程规则看仓库根目录 [`../../CODE_STYLE.md`](../../CODE_STYLE.md)。如果代码与本文件冲突，先判断代码是否越界，不要直接扩大白名单。

## 1. Top-level Dependency Matrix

Quality production 内部允许的 top-level 依赖：

| Source | Allowed Quality packages |
| --- | --- |
| `controller` | `asset`, `config`, `domain`, `execution`, `monitor`, `template`, `workspace` |
| `workspace` | `config`, `domain`, `monitor`, `repository` |
| `monitor` | `config`, `domain`, `repository`, `schedule`, `task` |
| `task` | `config`, `domain`, `execution`, `repository` |
| `schedule` | `config`, `domain`, `execution`, `repository` |
| `execution` | `alert`, `config`, `domain`, `gateway`, `repository` |
| `alert` | `config`, `domain`, `repository` |
| `asset` | `config`, `domain`, `gateway`, `repository` |
| `template` | `config`, `domain`, `repository` |
| `gateway` | `config` |
| `repository` | `config`, `dao`, `domain` |
| `dao` | none |
| `domain` | none |
| `config` | none |

同一 top-level package 内部可以互相协作，但不会因此自动成为其他 package 的公共 API。声明图和实际源码图都必须无环。

## 2. Controller Corridors

Controller 只能进入显式 Application role，不允许直接调用 Repository / DAO / Gateway / Schedule engine。

当前可用角色族：

```text
asset      -> Manager / Reader / CandidateReader
monitor    -> Manager / Reader
execution  -> Manager / Reader
workspace  -> Reader / Projector
template   -> Manager / Reader
```

Controller transport mapper 只负责 DTO/VO 与 Quality command/domain/read values 的边界转换。

## 3. Monitor -> Schedule / Task

Monitor 跨入 Schedule 只允许：

```text
QualityMonitorManager
    -> QualityScheduleLifecycle

QualityMonitorSettingsPolicy
    -> QualityScheduleCalculator
```

Monitor 发布工作流任务只允许：

```text
QualityMonitorManager
    -> QualityTaskPublisher
```

`QualityTaskPublisher` 负责把当前可执行 Monitor 冻结成 Quality-owned immutable revision，再更新 Task Catalog projection。Monitor 不直接操作 Task Catalog、revision repository 或 Workflow runtime。

## 4. Task -> Execution

Quality Workflow task adapter 进入 Execution 只允许：

```text
QualityTaskPublisher
    -> QualityExecutionPlanFactory

QualityTaskExecutor
    -> QualityExecutionManager
    -> QualityExecutionReader
    -> QualityExecutionReceipt
```

Task package 不复制质量规则执行逻辑，不直接调用 Datasource。真正执行仍由既有 Execution subsystem 完成。

`QualityTaskRevisionProvider` 只把 Quality-owned revision 解析成 Task Catalog 的稳定 `TaskSourceRevision`；不可变内容仍由 Quality 持有。

## 5. Schedule -> Execution

Schedule callback 进入 Execution 只允许：

```text
QualityScheduleHandler
    -> QualityExecutionManager
```

Schedule 不能直接：

- insert/update Execution；
- 调用 Worker；
- 调用 Dispatcher；
- 自己复制 execution admission 规则。

## 6. Workspace -> Monitor

Workspace 读取 Monitor 当前定义只允许：

```text
QualityWorkspaceReader
    -> QualityMonitorReader
```

其他 workspace projection 优先直接依赖自己的 read Repository；Workspace 不进入 Monitor Manager。

## 7. Execution -> Alert

Execution 触发告警只允许：

```text
QualityExecutionWorker
    -> QualityAlertRecorder
```

Execution 不直接写 Alert DAO/PO；Alert 也不反向调用 Execution Manager/Worker。

## 8. Quality-owned Datasource Gateway

Asset / Execution 使用 Datasource 能力时只依赖：

```text
QualityDataCatalogGateway
```

当前允许：

```text
asset     -> QualityDataCatalogGateway
execution -> QualityDataCatalogGateway
```

禁止 business role 直接 import：

```text
io.yak.ops.business.datasource.controller.*
io.yak.ops.business.datasource.repository.*
io.yak.ops.business.datasource.dao.*
io.yak.ops.business.datasource.plugin.*
```

### External Datasource corridor

Quality 对 Datasource 模块只有两个明确入口：

```text
config/QualityConfiguration
    -> datasource.config.BusinessDatabaseConfiguration
       # infrastructure wiring only

gateway/datasource/DataSourceQualityCatalogAdapter
    -> datasource.catalog.DataSourceCatalogReader
    -> datasource.domain.catalog.CatalogReadRequest
       # typed Catalog capability only
```

不得扩大为 Quality business package 直接依赖 Datasource implementation。

## 9. Cross-module Task Corridors

Quality 只通过稳定任务边界接入编排：

```text
quality.task
    -> yak-ops-business-task-catalog
       # publish / revision-provider / reconcile SPI
    -> yak-ops-business-job
       # TaskExecutor runtime contract
    -> yak-ops-spi
       # TaskAssetSource / shared task models
```

禁止 Quality 直接依赖 `yak-ops-business-workflow`。Workflow 只能经 Task Catalog + generic Job execution contract 消费 Quality。

## 10. Persistence Boundary

```text
Manager / Reader / Worker / Policy owner
    -> narrow Quality*Repository port
    -> RepositoryAdapter
    -> DAO / PO / Mapper XML
```

底层规则：

```text
Domain     -> no Quality upper-layer dependency
DAO        -> no Quality business dependency
Repository -> config + dao + domain only
```

Repository contract 不暴露：

- Quality HTTP DTO/VO；
- Quality PO；
- MyBatis `IPage` / Mapper；
- Datasource DTO/VO/PO。

分页 Repository 继续使用 shared `io.yak.framework.common.PageData<T>`。

Workflow 专用 execution admission/cancel 由 `QualityWorkflowExecutionRepository` 隔离，不扩大已有 `QualityExecutionRepository` 公共 contract。

## 11. Config Boundary

`config` 只负责：

- feature condition；
- properties；
- Flyway；
- executor；
- MapperScan；
- infrastructure configuration import。

业务角色不由 `QualityConfiguration` 手工 new / `@Bean` 反向装配。

因此禁止重新建立：

```text
config -> execution/monitor/asset/template/workspace/task
         -> config
```

内部专业角色应使用正常 constructor injection + `@Component`。

## 12. No Cycles

不允许通过以下方式掩盖 package cycle：

- `@Lazy`；
- ApplicationContext lookup；
- 静态 Service Locator；
- 把接口随意移动到第三个 `common` package；
- 扩大 dependency-test 白名单。

发现环时先明确能力 owner，再建立窄 Gateway / Reader / Lifecycle corridor。

## 13. Adding a New Dependency

新增一个 import 不在允许矩阵时按顺序判断：

1. **类是否放错 package？**
2. **已有 Manager / Reader / Gateway / Repository 是否能表达？**
3. **是否缺一个由能力 owner 定义的窄 contract？**
4. **架构是否真的改变？**

只有第 4 种情况才在同一个 PR 更新 `ARCHITECTURE.md`、本文件和 executable dependency test。
