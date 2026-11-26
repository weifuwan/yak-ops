# MiniCluster REST P0 验证

## 目标

验证 Flink 1.20.3 MiniCluster 是否能够在不使用反射、不固定 REST 端口的前提下：

1. 暴露可供 Worker 内部使用的真实 REST 地址。
2. 通过 REST 查询 Job 状态、Vertex 和 Subtask 自定义指标。
3. 在 Job 成功、取消、失败后继续访问 REST。
4. 在最终指标采集完成前保持 MiniCluster 存活，并由调用方显式关闭。

本阶段只增加验证代码和结论记录，不修改 DataSync 生产执行流程，不引入 Coordinator、RPC 或报告数据表。

## 当前执行路径的源码结论

当前 DataSync 执行链路为：

\`DataSyncJobRunner\`
→ \`FlinkPipelineComposer.ofMiniCluster(...)\`
→ \`StreamExecutionEnvironment.getExecutionEnvironment(...)\`
→ Flink \`LocalExecutor\`
→ \`PerJobMiniClusterFactory\`
→ \`MiniClusterJobClient\`

Flink 1.20.3 的 \`LocalExecutor\` 会在内部创建 MiniCluster。当前调用方拿到的是 \`JobClient\`，而 \`JobClient\` 接口没有公开 REST URL 获取方法；\`MiniClusterJobClient\` 内部持有 MiniCluster，但没有公开暴露该对象或 REST 地址。

另外，Flink 1.20.3 的 \`PerJobMiniClusterFactory\` 创建 \`MiniClusterJobClient\` 时使用 \`JobFinalizationBehavior.SHUTDOWN_CLUSTER\`。作业结果完成时会触发 MiniCluster 关闭。因此，现有隐式 LocalExecutor 路径不适合作为“作业终态后仍由 Coordinator 采集最终指标”的生命周期所有者。

## P0 验证实现

新增测试：

\`yak-ops-cdc/yak-ops-cdc-composer/src/test/java/org/apache/flink/cdc/composer/flink/MiniClusterRestApiPocTest.java\`

测试直接创建并持有 Flink MiniCluster：

- \`MiniClusterConfiguration.Builder.withRandomPorts()\` 分配随机端口。
- \`MiniClusterClient.getWebInterfaceURL()\` 获取实际 REST 地址。
- \`MiniClusterClient.submitJob(...)\` 提交 JobGraph。
- 通过 REST 查询 Job 状态和 Subtask 自定义指标。
- Job 终态后先完成 REST 查询，再由测试显式调用 \`MiniCluster.closeAsync()\`。

测试用 Source/Sink 自定义 Counter 模拟 CDC 指标名称：

- Source：\`numSnapshotRecords\`
- Sink：\`numCommittedRecords\`

分别验证 FINISHED、CANCELED、FAILED 三种终态。

## 执行命令

\`\`\`bash
mvn -pl yak-ops-cdc/yak-ops-cdc-composer -am \\
  -DskipUT=false \\
  -Dspotless.skip=true \\
  -Djacoco.skip=true \\
  -Danalyze.skip=true \\
  test
\`\`\`

仓库的 CDC Tests GitHub Actions 工作流会在本目录变更推送到 main 后自动执行 CDC 模块测试。


## 指标刷新间隔注意事项

Flink 1.20.3 的 `metrics.fetcher.update-interval` 默认值为 10 秒。REST 指标接口读取的是 JobManager MetricFetcher 刷新后的指标缓存，因此它不等同于每次 HTTP 请求都立即从 TaskManager 拉取最新值。

P0 测试将该参数设为 100ms，以缩短测试等待时间。生产环境需要结合指标采集周期和 JobManager 负载显式确定该参数；如果 Yak-OPS 仍采用 5 秒采集周期，不能忽略默认 10 秒的刷新间隔，否则页面可能连续读到相同快照。

## P1 设计输入

如果 P0 测试通过，P1 应采用“由 Yak-OPS 显式拥有 MiniCluster 生命周期”的执行方式：

1. Pipeline 仍由现有 CDC Parser、PipelineDef 和 Composer 生成。
2. 将 StreamGraph 转成 JobGraph。
3. 由受控的 MiniCluster 提交 JobGraph。
4. 保存 JobID 和实际 REST URL。
5. Job 终态后先采集最终快照，再调用 closeAsync。

如果终态后 REST 指标不可读，则不能依赖终态后的 REST 查询补齐最终计数；需要在关闭前采集，或将必要计数通过 Flink 累加器/执行结果另行保留。

本 P0 不决定最终生产执行类的组织方式，也不修改现有 DataSyncJobRunner。
