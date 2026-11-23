---
name: current-time
description: 服务器时间口径——何时必须调用 get_current_time、返回字段含义（平台时区 / UTC / epoch）、与任务实例 startTime/endTime（UTC 格式）对比、相对时间窗口换算。用户问“现在几点/今天/昨天/最近 N 小时”，或需要判断实例运行时长与日志时间先后时使用。
---

# 服务器当前时间

## 适用场景

用户问到当前时间、日期、星期，或使用“今天 / 昨天 / 最近 N 小时 / 本周”等相对时间；需要判断数据同步实例是否落在某个时间窗口、运行了多久、日志时间先后。

## 规则

1. 回答时间问题或计算相对时间窗口前，先调用 `get_current_time`，不要使用模型记忆中的日期，也不要自行推算。
2. 上下文里注入的 `<current-time>` 只是快照，可能已经过时；需要精确时间时以 `get_current_time` 的返回值为准。
3. 一轮对话内通常调用一次即可；操作跨度很长、需要精确时间时再调用。
4. 工具失败时如实说明无法获取服务器时间，不要编造。

## 返回字段

| 字段 | 含义 |
| --- | --- |
| `timezone` | 平台时区（Asia/Shanghai） |
| `iso` | 平台时区的 ISO 8601 时间，带偏移（如 `2026-11-23T14:32:05+08:00`） |
| `utc` | UTC 的 ISO 8601 时间（如 `2026-11-23T06:32:05Z`） |
| `apiUtcTime` | 平台接口的时间格式（`2026-11-23 06:32:05`，UTC） |
| `date` / `time` / `weekday` | 平台时区的日期、时刻、星期 |
| `epochMillis` | Unix 毫秒时间戳，用于精确比较 |

## 时间口径

- **平台接口返回的 `createTime` / `startTime` / `endTime` 是 UTC**，格式为 `yyyy-MM-dd HH:mm:ss`，不带时区后缀；与 `apiUtcTime` 或 `utc` 比较，不要直接和 `iso`（+08:00）比较。
- 向用户展示时间时使用平台时区；UTC 时间加 8 小时或按 `iso` 口径描述。
- “今天 / 昨天”按平台时区日期划分（UTC 16:00 之后平台日期才进入次日）。
- 相对窗口用毫秒时间戳计算：`[now - N * 3600_000, now]`，避免手写日期字符串。
- 耗时 = `endTime - startTime`；`endTime` 为空表示实例未结束，不能计算耗时。

## 示例

用户问“最近 2 小时有哪些失败实例”：

1. `get_current_time` 得到 `epochMillis` / `apiUtcTime`；
2. `list_sync_instances(status=FAILURE)` 取实例；
3. 用实例 `startTime` 与 `apiUtcTime` 比较，筛出 2 小时内的实例；
4. 回答时把时间换算成平台时区（+08:00）。