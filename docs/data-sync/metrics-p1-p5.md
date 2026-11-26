# Yak-OPS DataSync 指标链路 P1–P5

## 目标与边界

本次实现保持 Workflow 核心调度和实例生命周期不变。CDC 的执行生命周期、指标采集、报告 RPC、报告持久化和查询接口均由 DataSync 专属模块负责。

## 模块边界

- Worker 插件：`yak-ops-task-data-sync`，包含 Execution、Metrics、Report 三组 Interface / Abstract / Concrete 实现，以及 DataSyncExecutionCoordinator。
- Flink CDC：Composer 只负责生成 JobGraph；SourceReaderMetrics 保留现有读取计数，JDBC Sink 增加成功 Flush 后的 `numCommittedRecords`。
- RPC 契约：`yak-ops-extract-master`，通过独立 `IDataSyncReportListener` 从 Worker 向 Master 传输报告。
- Master：RPC Listener 只接收请求并调用业务 Service。
- Service / DAO：`yak-ops-service` 负责序号、幂等和最终快照规则；`yak-ops-dao` 负责两张报告表。
- API：`GET /projects/{projectCode}/data-sync/instances/{id}/report` 查询当前实例最近一次执行报告。

## 指标口径

- `readRecords` = `snapshotRecords + insertRecords + updateRecords + deleteRecords`。
- `committedRecords` 只在 JDBC `flush()` 成功后累计，不代表目标表去重后的净增行数。
- DDL 和错误数量单独统计，不计入读取条数。
- 未采集到的指标保持 `null`，不伪装成 0。
- Source 和 Sink 的表级指标使用 Flink MetricGroup 的 namespace/schema/table 维度。

## 报告规则

- 每次 Flink Job 生成独立 `executionId`，不复用 TaskInstance ID。
- Worker 每 5 秒发送 `SNAPSHOT`，任务终态时发送 `FINAL`；报告携带 Worker 提交时间，查询时优先按提交时间选取 TaskInstance 的最新执行。
- Master 根据 `executionId + sequence` 幂等接收；旧序号不能覆盖新快照。
- FINAL 被接受后不可再被普通 SNAPSHOT 覆盖。
- 指标采集或报告发送失败不会改变 CDC 作业本身的成功/失败结果；Worker 会保留最后一次成功快照并记录指标状态。
- MiniCluster 在最终报告发送尝试完成后释放；远程部署实现可复用 Metrics 和 Report 服务，只替换 Execution 实现。

## FINAL 报告补偿

Worker 在 FINAL 报告连续 RPC 失败后，会将报告和目标 Master 地址写入本地 Outbox，再由后台线程每 30 秒重试，直到 Master 返回已接受的 ACK。默认目录为 `~/.yak-ops/data-sync-report-outbox`，可通过 JVM 参数 `-Dyak.ops.data-sync.report.outbox-dir=/持久化目录` 修改。

生产部署应将该目录挂载到 Worker 的持久化磁盘；容器重建时如果使用临时文件系统，无法保证 Worker 重启后仍保留待发送报告。相同 executionId + sequence 的补发依赖 Master 幂等处理，不会重复生成最终记录。

## 数据库升级

新增表：

- `t_yak_ops_data_sync_execution_report`
- `t_yak_ops_data_sync_table_report`

新安装环境的 MySQL、PostgreSQL、H2 初始化 SQL 已同步更新。已有环境请按实际数据库执行对应补丁：

- MySQL：`yak-ops-dao/src/main/resources/sql/patches/data-sync-report_mysql.sql`
- PostgreSQL / OpenGauss：`yak-ops-dao/src/main/resources/sql/patches/data-sync-report_postgresql.sql`
- H2：`yak-ops-dao/src/main/resources/sql/patches/data-sync-report_h2.sql`

## 部署与验证

当前 Execution 实现为 `LOCAL_MINICLUSTER`。YARN / Kubernetes / Standalone 后续只需新增 `DataSyncExecutionService` 的 Abstract 子类，不需要改 Metrics REST 采集、RPC 协议、报告表或 Workflow 核心。

建议验证：

1. 单表全量同步：读取量与 Sink 成功 Flush 量。
2. 单表增量同步：INSERT / UPDATE / DELETE 分类。
3. 多表同步：表级 Source / Sink 指标聚合。
4. 取消、失败、成功：FINAL 报告及最终快照固化。
5. Worker 到 Master RPC 超时重试：相同 sequence 不重复写入。
6. API 按项目权限查询：不能读取其他项目的 TaskInstance 报告。
