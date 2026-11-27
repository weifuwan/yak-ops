---
name: data-sync-create
description: 数据同步任务创建/修改的参数结构——MySQL CDC 与 JDBC BATCH Source → JDBC Sink 的 Pipeline definition、单表/多表路由、Transform 复用、数据源 ID 与常见校验错误。新建或修改同步任务、项目内没有现成任务可抄时使用。
---

# 数据同步任务创建

## 适用场景

用户要新建或修改数据同步任务（尤其是 MySQL → 数仓贴源库），或项目里没有现成任务可以复制模板时。

## 参数总结构（必须逐层保留）

调用 `preview_sync_task` / `create_sync_task` 时，`parameters_json` 必须是下面这个对象的 JSON 文本：

```json
{
  "definition": {
    "source": { "...": "..." },
    "sink": { "...": "..." },
    "route": [ { "...": "..." } ],
    "pipeline": { "...": "..." }
  }
}
```

硬规则：

- `source` / `sink` / `route` / `pipeline` 都在 `definition` 内，不在 `parameters` 顶层，也不要再套 `pipeline.definition`。
- `datasource-id` 必须是**字符串**（`"2"`，不是 `2`）。
- 不允许写 `password` / `token` / `secret` 等凭据；连接信息由数据源 id 在运行时注入。
- `source.type` 支持 `mysql`（CDC）或 `jdbc`（V1 全量离线读取），`sink.type` 固定 `jdbc`。
- JDBC Source V1 使用一个通用 JDBC Source，支持已安装的 JDBC 数据源插件（不含 SSH）、显式单表或多表，不支持整库通配符、自定义 SQL、增量捕获。
- `route` 必须与 `source.tables` 一一对应：每个源表恰好一条映射，`sink-table` 不能重复。

## 最小骨架（单表）

```json
{
  "definition": {
    "source": {
      "type": "mysql",
      "datasource-id": "2",
      "tables": "ecommerce_demo.customers",
      "scan.startup.mode": "initial"
    },
    "sink": {
      "type": "jdbc",
      "datasource-id": "3",
      "exclude.schema.changes": ["drop.column", "truncate.table", "drop.table"]
    },
    "route": [
      { "source-table": "ecommerce_demo.customers", "sink-table": "ods.customers" }
    ],
    "pipeline": {
      "execution.runtime-mode": "STREAMING",
      "schema.change.behavior": "evolve",
      "parallelism": 1
    }
  }
}
```

- `source.tables` / `route.source-table` 必须是 `database.table`。
- `route.sink-table` 也是 `database.table`；目标表不存在时由 `schema.change.behavior=evolve` 自动建表。
- `sink.exclude.schema.changes` 可选，取值：`create.table` / `add.column` / `alter.column.type` / `rename.column` / `drop.column` / `truncate.table` / `drop.table` / `alter.table.comment`。
- `pipeline.parallelism` 可选，1–128，默认 1。

## 启动模式与运行时模式（强联动）

| 同步内容 | source.scan.startup.mode | pipeline.execution.runtime-mode |
| --- | --- | --- |
| 全量 + 增量 | `initial` | `STREAMING` |
| 仅全量 | `snapshot` | `BATCH` |
| 仅增量 | `latest-offset` | `STREAMING` |

两者不一致会被直接拒绝。

## JDBC Source V1（全量离线同步）

当用户选择仅全量同步时，使用 `source.type=jdbc`，运行模式必须为 `BATCH`。数据源连接信息仍只通过 `datasource-id` 在运行时注入，不允许在任务定义中写入 JDBC URL、用户名或密码。

```json
{
  "definition": {
    "source": {
      "type": "jdbc",
      "datasource-id": "2",
      "tables": "public.orders,public.customers",
      "scan.fetch-size": 1000,
      "scan.split-size": 100000
    },
    "sink": {
      "type": "jdbc",
      "datasource-id": "3",
      "exclude.schema.changes": ["drop.column", "truncate.table", "drop.table"]
    },
    "route": [
      { "source-table": "public.orders", "sink-table": "ods.orders" },
      { "source-table": "public.customers", "sink-table": "ods.customers" }
    ],
    "pipeline": {
      "execution.runtime-mode": "BATCH",
      "schema.change.behavior": "evolve",
      "parallelism": 4
    },
    "transform": [
      {
        "source-table": "public.orders",
        "projection": "id,customer_id,amount",
        "filter": "amount > 0",
        "primary-keys": "id"
      }
    ]
  }
}
```

- JDBC Source 使用 `scan.fetch-size`（默认 1000）和 `scan.split-size`（默认 100000）。
- 单列数值型主键自动范围分片；无主键、复合主键或非数值主键退化为单 Split。
- 字段增减、过滤、主键声明全部复用现有 `transform.projection` / `transform.filter` / `transform.primary-keys`，不要在 Source 配置中重复声明字段映射。
- JDBC Source 是 Bounded Source，不得配置 `scan.startup.mode`、CDC chunk key 或 Streaming runtime mode。
- V1 不承诺多个 JDBC Connection 之间的全局一致性快照；失败恢复按 Split 级别 At-Least-Once 处理。

## 多表 / 整库

**多表一个任务**：`source.tables` 用逗号分隔，`route` 为每张表各写一条：

```json
{
  "definition": {
    "source": { "type": "mysql", "datasource-id": "2", "tables": "ecommerce_demo.orders,ecommerce_demo.customers", "scan.startup.mode": "initial" },
    "sink": { "type": "jdbc", "datasource-id": "3", "exclude.schema.changes": ["drop.column", "truncate.table", "drop.table"] },
    "route": [
      { "source-table": "ecommerce_demo.orders", "sink-table": "ods.orders" },
      { "source-table": "ecommerce_demo.customers", "sink-table": "ods.customers" }
    ],
    "pipeline": { "execution.runtime-mode": "STREAMING", "schema.change.behavior": "evolve", "parallelism": 1 }
  }
}
```

**整库一个任务**（仅 MySQL CDC 模式，含未来新增表）：`source.tables` 用 Flink CDC 的整库正则 `database.\.*`（JSON 文本里反斜杠要转义成 `database.\\.*`），`route` 只能有一条且必须带 `replace-symbol`：

```json
{
  "definition": {
    "source": { "type": "mysql", "datasource-id": "2", "tables": "ecommerce_demo.\\.*", "scan.startup.mode": "initial" },
    "sink": { "type": "jdbc", "datasource-id": "3", "exclude.schema.changes": ["drop.column", "truncate.table", "drop.table"] },
    "route": [ { "source-table": "ecommerce_demo.\\.*", "sink-table": "ods.<>", "replace-symbol": "<>" } ],
    "pipeline": { "execution.runtime-mode": "STREAMING", "schema.change.behavior": "evolve", "parallelism": 1 }
  }
}
```

整库任务不支持 `transform`，也不支持 chunk key。用户要求“每张表一个任务”时用单表骨架逐表创建；否则优先建议整库任务（作业数少，后续新表自动纳入）。

## 字段映射（可选）

`definition.transform` 是数组，每项以 `source-table` 定位，只支持 `projection` / `filter` / `primary-keys` 三个字符串字段。在 `definition` 内追加：

```json
{
  "transform": [
    { "source-table": "ecommerce_demo.orders", "primary-keys": "id", "projection": "id,user_id,amount", "filter": "amount > 0" }
  ]
}
```

## 取值与执行流程

1. `list_data_sources` / `get_data_source` 确认源、目标数据源 id 与 database（禁止编造 id、库名、表名）。
2. `list_data_source_tables` / `list_data_source_columns` 取真实表名、字段与主键。
3. 按上面的骨架组装 `parameters_json`（JSON 文本，对象结构）。
4. `preview_sync_task(name=..., parameters_json=...)` 校验；把渲染出的 YAML 摘要给用户看。
5. 用户确认后 `create_sync_task(...)`：任务默认 OFFLINE，需要时再 `publish_sync_task`。

## 空项目 / 没有模板时

项目内 `list_sync_tasks` 为空、`get_sync_task` 无可复制模板时，**直接使用本技能的最小骨架**，禁止猜字段层级或字段名。

**止损规则**：同一个工具因参数结构连续失败 2 次，停止排列组合；重新阅读本技能，或向用户索要任意一个已有任务的 `parameters` 作为样例。

## 常见校验错误对照

| 错误信息 | 原因 |
| --- | --- |
| `parameters: definition: object required` | 缺少 `definition`，或把 `source/sink` 放在了 `parameters` 顶层 |
| `parameters: source/sink/pipeline: object required` | 放错层级（如塞进 `pipeline` 或 `definition` 之外） |
| `datasource-id must be a positive integer string` | 写成了数字 `2`，应为字符串 `"2"` |
| `source.tables: expected unique database.table values` | 少了库名前缀、表名重复，或格式不是 `db.table` |
| `route: every source table must have exactly one target mapping` | `route` 条数与 `source.tables` 不一致 |
| `route[0].sink-table: duplicate target mapping` | 两个源表映射到同一目标表 |
| `pipeline.execution.runtime-mode does not match ...` | 启动模式与运行时模式不满足上面的矩阵 |
| `Pipeline must reference Yak Ops datasources, not inline credentials` | 写了 `password` / `token` 等内联凭据 |