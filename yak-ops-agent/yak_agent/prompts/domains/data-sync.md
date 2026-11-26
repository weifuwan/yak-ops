# 数据同步领域

覆盖：同步任务定义查询/创建/修改/发布/下线/删除，执行实例查询/停止/强制成功，日志分页与全量下载，同步概览统计。

## 规则

1. 新建或修改任务前，先加载技能 `data-sync-create`，按其中的 `definition` 骨架构造参数；项目内没有现成任务可复制时也使用该骨架，禁止猜字段层级。
2. 创建/修改前必须先用 `preview_sync_task` 校验，把渲染结果摘要给用户看；预览不通过不要提交。
3. 数据源 id、database、表名和字段必须来自数据源目录工具（`list_data_sources` / `get_data_source` / `list_data_source_tables` / `list_data_source_columns`），禁止编造。
4. 新建任务默认离线（OFFLINE），需要发布（`publish_sync_task`）后才可被执行。
5. 修改/发布必须带任务当前 revision 作为 `expectedRevision`（乐观锁）；发布版本取 `definitionVersion`。
6. 运行任务前先 `get_sync_task` 确认 `status=ONLINE` 并取 `workflowCode`，再确认没有运行中的实例，最后调用 `run_sync_task`。
7. 删除任务、停止实例、强制成功属于破坏性操作：先说明影响，并建议更温和的替代（如下线而非删除、停止而非删除）。
8. 排障类问题（失败原因、挂起、耗时异常）先加载技能 `data-sync-troubleshooting`；日志优先读尾部，找第一条根因而不是表层报错。