# Yak Ops 数据同步目标表映射

本文档记录当前可使用的目标表映射能力及其边界。界面参考了 [DataWorks 整库实时同步配置](https://help.aliyun.com/zh/dataworks/user-guide/configure-a-data-synchronization-solution-in-data-integration)，但不代表逐项实现了 DataWorks 的全部映射规则功能。

## 当前实现

### 显式选表

用户选中来源表后，可在“目标表映射”中直接编辑目标库名、Schema 和目标表名。界面通过 `DataSyncTableMapping.source` 与 `DataSyncTableMapping.target` 描述一对一表映射，不需要另设“目标库名映射自定义”“目标表名映射自定义”列。

保存任务时，`buildSyncParameters()` 将表映射写入 `source.tables`、`sink.tables` 和 `route`。Task Plugin 的 `PipelineYamlConverter` 把这些 JSON 参数转换成 Flink CDC 风格的 YAML，Engine 使用 `PipelineOptions` / `TableIdRouter` 解析，再由 `SchemaTranslator` 执行表路由。实际目标命名需要通过数据源绑定的命名空间和表结构校验。

```yaml
route:
  - source-table: cdc_lab.orders
    sink-table: yak_e2e_target.ods_orders
```

以上是路由示例，不代表支持跨越目标数据源已绑定的数据库。

### 批量修改

当前的 `BatchMappingDialog` 可以批量调整目标库名、目标表模板和写入配置。模板展开由 `applyTableTemplate()` 实现，支持 `${database}`、`${schema}`、`${table}`。目标重名检查针对最终生成的物理名称，不需要单独保存规则 ID。

### REGEX 动态选表

正则选表使用 `source.selection` 与 `sink.mapping` 的独立配置。Task Plugin 转换为 `yak.selection.*` / `yak.mapping.*`，Engine 中的 `DynamicTableSelection` 使用命名模板生成**后续发现的表**的目标路径。这是实际支持的动态选表功能，不应与已清理的前端可视化规则编辑器混淆。

## 不属于当前执行协议的功能

旧版 DataWorks 对齐原型曾探索可视化规则编辑、前后缀和替换操作、`DataSyncNameMappingRule`、各类 `*MappingRuleId` 及“根据规则刷新映射”。这些组件没有接入当前任务保存及执行协议，已从前端移除；任务不接受独立的 `mappingRules` 配置。不要为了兼容这些未发布的原型字段增加新协议或后端分支。

旧版文档还涉及目标表结构编辑、动态分区赋值、增量流水表等探索方向。相关类型或原型组件是否存在，不等同于它们已进入实际 Task Plugin / Engine 执行链路；应分别以当前代码、Connector 能力和测试为准，不在本文宣称已完成 DataWorks 对齐。

## 相关源码

- `yak-ops-ui/apps/web/app/data-sync/editor/replication-mapping-table.tsx`：显式表映射与编辑入口
- `yak-ops-ui/apps/web/app/data-sync/editor/mapping-settings-dialog.tsx`：目标库、Schema、表名配置
- `yak-ops-ui/apps/web/app/data-sync/editor/batch-mapping-dialog.tsx`：批量目标命名模板
- `yak-ops-ui/apps/web/service/data-sync/definition.ts`：构建持久化 Pipeline JSON
- `yak-ops-ui/apps/web/service/data-sync/table-configuration.ts`：模板展开
- `yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-data-sync/src/main/java/io/yak/ops/plugin/task/datasync/PipelineYamlConverter.java`：JSON 到 YAML
- `yak-ops-engine/src/main/java/io/yak/ops/composer/definition/DynamicTableSelection.java`：正则选表的动态目标命名
- `yak-ops-engine/src/main/java/io/yak/ops/composer/yakflow/translator/SchemaTranslator.java`：实际表路由

参见 [Data Sync Pipeline](./data-sync-pipeline.md) 和 [Frontend Rules](../yak-ops-ui/FRONTEND_RULES.md)。
