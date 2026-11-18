---
name: data-sync-troubleshooting
description: 数据同步排障知识——实例状态语义与处置、按序排查步骤、常见根因模式（唯一约束/Flink CDC/连接失败）、耗时口径与高危操作红线。排查同步失败、实例挂起、耗时异常时使用。
---

# 数据同步排障

## 适用场景

用户在排查数据同步问题时加载本技能：任务为什么失败、实例一直挂起、耗时异常、要不要重跑或停止。

## 状态语义（实例 status → 含义与处置）

| 状态 | 含义 | 处置建议 |
| --- | --- | --- |
| SUBMITTED_SUCCESS / DISPATCH | 已提交、调度中 | 尚未开始执行，等待即可 |
| RUNNING_EXECUTION | 运行中 | 需要停止用 stop_task_instance（需确认） |
| FAILURE | 执行失败 | 读日志尾部定位根因 |
| SUCCESS | 成功 | — |
| KILL / STOP | 被手动停止或取消 | 确认是否为人为操作 |
| PAUSE / READY_PAUSE | 暂停 | — |
| NEED_FAULT_TOLERANCE | **挂起未结束**（有 startTime 无 endTime） | 不是失败，是卡住；可停止后重跑 |

## 排查步骤（按序执行，先取数再下结论）

1. `list_sync_instances(status=FAILURE, page_size=50)` 或全量拉取，确认目标实例与时间；
2. `get_sync_instance_detail(instance_id)` 看状态、起止时间、workflowInstanceId；
3. `query_task_log(task_instance_id, limit=200)` 读日志**尾部**——根因通常在最后几屏；
4. 在日志中优先找 `ERROR` / `Exception` / `Caused by` 行，取**第一条根因**而不是表层报错。

## 常见根因模式

- **唯一约束冲突**（`duplicate key ... violates unique constraint`）：目标表主键/唯一键冲突 → 建议 upsert 或去重；
- **Flink CDC job execution failed**：表层原因，需继续向下找 `Caused by`；
- **连接失败 / timeout**：数据源不可达，检查网络与账号；
- **数据倾斜 / checkpoint 失败**：看 Flink 侧日志，建议调并行度或 checkpoint 间隔。

## 耗时口径

- 耗时 = `endTime - startTime`；`endTime` 为空表示未结束（NEED_FAULT_TOLERANCE），不能参与耗时排序；
- 列表接口不返回 duration 字段，必须自行计算；跨分页比较时先取全量再排序。

## 高危操作红线

- 删除任务前先建议 `unpublish_sync_task`（仅停用）；
- `force_success_task_instance` 只在确认数据实际无误时使用（会把失败标成功）；
- 停止运行中实例前确认实例确实在运行态。
