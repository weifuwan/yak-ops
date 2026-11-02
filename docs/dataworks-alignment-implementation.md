# DataWorks 目标表映射设计记录

> 历史设计材料：本文包含表结构、动态分区和增量流水等原型方案，不代表全部接入当前任务保存或执行协议。实际可用能力以代码、Connector 能力和测试为准；当前已移除未接入的独立名称映射规则编辑器。

本文档记录 Yak Ops 数据同步目标表映射与阿里云 DataWorks 交互设计的探索。

## 实现概览

根据 [DataWorks 整库实时同步文档](https://help.aliyun.com/zh/dataworks/user-guide/configure-a-data-synchronization-solution-in-data-integration)，以下包括已接入的表路由，以及尚需单独验证执行链路的原型：

### 映射实现与历史原型

#### 1. 显式目标库、Schema、表名映射

当前映射直接编辑 `DataSyncTableMapping.target`；`buildSyncParameters()` 保存 `source.tables`、`sink.tables` 和 `route`，Task Plugin 的 `PipelineYamlConverter` 转换为 Pipeline YAML，再由 Engine 解析并执行表路由。无需额外存储映射规则 ID；独立规则编辑器及其规则计算工具未接入任务，已经清理。

批量配置仍由 `BatchMappingDialog` 和 `applyTableTemplate()` 实现。REGEX 选表保留 `sink.mapping` 模板与 Engine `DynamicTableSelection`，用于后续发现的新表。

#### 2. 目标表结构编辑器

**位置**: `yak-ops-ui/apps/web/app/data-sync/editor/target-structure-editor-dialog.tsx`

完整的表结构编辑器，支持：

- **字段管理**:
  - 新增/删除字段
  - 配置字段类型、可空性、默认值、注释
  - 从源表导入字段
  - 字段去重验证

- **表类型** (MaxCompute):
  - 普通表 (`NORMAL`)
  - PK Delta Table (`PK_DELTA`)
  - Append Delta Table (`APPEND_DELTA`)

- **分区字段配置** (详见下节)

**类型定义**:
```typescript
interface DataSyncTargetTableStructure {
  columns: DataSyncTargetColumn[];
  partitions?: DataSyncTargetPartition[];
  tableType?: "NORMAL" | "PK_DELTA" | "APPEND_DELTA";
  tableProperties?: Record<string, string>;
}
```

#### 3. 动态分区字段配置

**位置**: `yak-ops-ui/apps/web/app/data-sync/editor/target-structure-editor-dialog.tsx` (PartitionAssignmentDialog)

与 DataWorks 完全对齐的分区配置，支持：

- **常量值分区**:
  ```typescript
  { type: "CONSTANT", value: "default" }
  ```

- **来源字段枚举值分区**:
  ```typescript
  { 
    type: "SOURCE_FIELD", 
    field: "region", 
    valueType: "ENUM" 
  }
  // 字段 region 的值 (如 "cn", "us") 直接作为分区值
  ```

- **来源字段时间值分区**:
  ```typescript
  {
    type: "SOURCE_FIELD",
    field: "created_at",
    valueType: "TIMESTAMP",
    sourceFormat: "TIME_STRING",
    sourcePattern: "yyyy-MM-dd HH:mm:ss",
    targetFormat: "yyyyMMdd"
  }
  // 字段 "2024-01-15 10:30:00" → 分区 "20240115"
  ```

- **事件时间分区**:
  ```typescript
  {
    type: "EVENT_TIME",
    sourceFormat: "UNIX_TIMESTAMP_MILLIS",
    targetFormat: "yyyyMM"
  }
  // CDC 事件时间戳 → 按月分区
  ```

- **时间格式支持**:
  - 时间字符串 (需指定格式)
  - 时间对象 (Date/Datetime)
  - Unix 时间戳(秒)
  - Unix 时间戳(毫秒)

- **安全限制**:
  - 提示分区数过多风险
  - 单日新增分区超过 1000 个将失败
  - 警告秒级/毫秒级分区风险

#### 4. 增量流水表格式定义

**位置**: `yak-ops-ui/apps/web/service/data-sync/types.ts`

定义了与 DataWorks 一致的增量流水表元信息字段：

```typescript
interface DataSyncIncrementalStreamMetadata {
  sequenceId: string;      // sequence_id: 唯一递增记录ID
  operationType: string;   // operation_type: I/D/U 或 DDL
  executeTime: string;     // execute_time: 数据对应时间戳
  beforeImage: string;     // before_image: Y/N (变更前)
  afterImage: string;      // after_image: Y/N (变更后)
  srcDatasource: string;   // src_datasource: 数据来源的数据源
  srcDatabase: string;     // src_database: 数据来源的数据库
  srcTable: string;        // src_table: 数据来源的表
  ddlSql?: string;         // ddl_sql: DDL 语句 (DDL操作时)
}

type DataSyncIncrementalStreamFormat = "FLAT_COLUMNS" | "JSON_COLUMNS";
```

两种格式：
- `FLAT_COLUMNS`: 源表字段平铺，元信息作为额外列
- `JSON_COLUMNS`: 源表字段合并为 JSON，元信息作为额外列

#### 5. 现行映射与批量修改

- **刷新映射状态**：刷新目标端目录元数据，不会运行历史原型中的“重算名称映射规则”。
- **批量修改**：使用 `BatchMappingDialog` 为选中表设置目标库名、表名模板与写入配置。
- **映射校验**：显式任务通过 `source.tables`、`sink.tables`、`route` 检查一对一映射和目标冲突。

#### 6. UI 组件

**目标表结构编辑原型**：`target-structure-editor-dialog.tsx`
- 字段和类型配置
- 分区字段及赋值配置
- 实际 Task Plugin / Engine 接入程度以相应源码为准

### 🔄 集成点

#### 在 `DataSyncTableMapping` 中的扩展

```typescript
interface DataSyncTableMapping {
  source: DataSyncTablePath & { /* ... */ };
  target: DataSyncTablePath & {
    primaryKeys?: string[];
    structure?: DataSyncTargetTableStructure; // 新增: 表结构定义
  };
  writeMode: DataSyncWriteMode;
  // ... 其他字段
  
  // 新增: 增量流水格式
  incrementalStreamFormat?: DataSyncIncrementalStreamFormat;
}
```

#### 在 `DataSyncCapabilities` 中的扩展

```typescript
interface DataSyncCapabilities {
  // ... 现有能力
  
  // 新增能力标识
  targetStructureEditor: boolean;   // 支持目标表结构编辑
  partitionFields: boolean;         // 支持分区字段配置
  incrementalStream: boolean;       // 支持增量流水格式
}
```

### 📋 与 DataWorks 的对比

| 功能 | 当前 Yak Ops 状态 |
|------|------------------|
| 逐表指定目标库/Schema/表 | 已接入 `sink.tables` + `route` |
| 批量目标表模板 | 已接入 `BatchMappingDialog` |
| REGEX 新表动态命名 | 已接入 `sink.mapping` / `DynamicTableSelection` |
| 独立的前后缀/替换名称映射规则编辑器 | 原型未接入，已清理 |
| 目标表结构编辑、分区赋值与增量流水 | 历史探索，须分别验证实际执行能力 |
| 字段类型映射、字段赋值表达式 | 使用各自独立的同步配置与执行链路 |

## 使用示例

### 1. 显式源表到目标表路由

```yaml
route:
  - source-table: cdc_lab.orders
    sink-table: yak_e2e_target.ods_orders
```

该路径须符合目标数据源绑定的命名空间。

### 2. 配置目标表结构原型

```typescript
const structure: DataSyncTargetTableStructure = {
  columns: [
    { name: "id", type: "BIGINT", nullable: false },
    { name: "name", type: "STRING", nullable: true },
    { name: "created_at", type: "TIMESTAMP", nullable: false },
  ],
  partitions: [
    {
      name: "dt",
      assignment: {
        type: "SOURCE_FIELD",
        field: "created_at",
        valueType: "TIMESTAMP",
        sourceFormat: "TIME_OBJECT",
        targetFormat: "yyyyMMdd",
      },
    },
  ],
  tableType: "PK_DELTA",
};
```

### 3. 批量目标表模板

批量修改可设置 `目标表模板`，支持 `${database}`、`${schema}`、`${table}` 占位符；实际表名按来源表展开后写入目标映射。不再保存独立的规则引用。

## 后端集成要点

### 1. 实际持久化与 Pipeline 转换

当前 Task Definition 持久化 `source.tables`、`sink.tables`、`route`；任务插件通过 `PipelineYamlConverter` 转换至 YAML，Engine 通过 `TableIdRouter` 和 `SchemaTranslator` 执行。

正则选表的 `source.selection` 与 `sink.mapping` 是单独的动态命名模板，不等同于前端历史的规则编辑原型。不要引入未发布的 `mappingRules`、`*MappingRuleId` 字段或后端兼容层。

### 2. 目标表创建（设计草案）

在 `SchemaPreparation` 阶段处理表结构定义：

```java
// 根据 structure 创建目标表
if (mapping.getTarget().getStructure() != null) {
    DataSyncTargetTableStructure structure = 
        mapping.getTarget().getStructure();
    
    // 创建表 DDL
    catalog.createTable(
        targetPath,
        structure.getColumns(),
        structure.getPartitions(),
        structure.getTableType(),
        structure.getTableProperties()
    );
}
```

### 3. 增量流水写入（设计草案）

在 Sink 实现中支持增量流水格式：

```java
if (mapping.getIncrementalStreamFormat() == FLAT_COLUMNS) {
    // 添加元信息列
    row.addColumn("_sequence_id", event.getSequenceId());
    row.addColumn("_operation_type", event.getOperationType());
    row.addColumn("_execute_time", event.getExecuteTime());
    // ... 其他元信息列
    // 源表字段平铺
    event.getData().forEach(row::addColumn);
} else {
    // JSON_COLUMNS 格式
    row.addColumn("_sequence_id", event.getSequenceId());
    // ... 其他元信息列
    row.addColumn("data_columns", toJson(event.getData()));
}
```

## 注意事项

### 1. 分区字段限制

- 单日新增分区数超过 1000 个会导致任务失败
- 避免使用秒级、毫秒级时间格式作为分区
- 推荐使用日期级别分区 (`yyyyMMdd`, `yyyyMM`)
- 枚举值分区建议使用有限枚举的字段

### 2. 表路由校验

- 显式来源和目标表均应唯一，不能将多个源表隐式合并到同一目标。
- 目标库和 Schema 必须符合数据源绑定范围；目标表按配置验证存在性或建表策略。
- 动态发现规则需验证目标命名模板，避免目标名冲突与重复捕获。

### 3. 表结构编辑

- 字段名和类型不能为空
- 字段名不能重复
- 分区字段名不能与普通字段重复
- 表类型更改可能影响写入模式兼容性

### 4. 已发布配置

- 已有任务的映射配置保持不变
- 已发布配置继续通过显式 `route` 或 REGEX 目标模板路由，不依赖已移除的 UI 规则 ID。

## 未来扩展

1. **可视化规则测试器**: 提供规则应用预览工具
2. **映射规则模板库**: 预置常用映射规则模板
3. **智能映射建议**: 基于历史任务推荐映射规则
4. **映射冲突自动解决**: 提供冲突自动修复建议
5. **分区策略优化**: 分析数据特征推荐最优分区方案

## 参考文档

- [DataWorks 整库实时同步配置](https://help.aliyun.com/zh/dataworks/user-guide/configure-a-data-synchronization-solution-in-data-integration)
- [Yak Ops 数据同步管道文档](./data-sync-pipeline.md)
- [Frontend Rules](../yak-ops-ui/FRONTEND_RULES.md)
