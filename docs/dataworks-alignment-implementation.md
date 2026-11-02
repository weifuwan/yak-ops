# DataWorks 目标表映射功能对齐实现

本文档记录了 Yak Ops 实时同步任务与阿里云 DataWorks 数据集成目标表映射配置的对齐实现。

## 实现概览

根据 [DataWorks 整库实时同步文档](https://help.aliyun.com/zh/dataworks/user-guide/configure-a-data-synchronization-solution-in-data-integration)，我们实现了以下核心功能：

### ✅ 已完成的功能

#### 1. 目标库名/Schema/表名映射规则引擎

**位置**: `yak-ops-ui/apps/web/service/data-sync/name-mapping-rules.ts`

实现了与 DataWorks 完全对齐的映射规则引擎，支持：

- **内置变量**:
  - `SOURCE_DATASOURCE`: 源端数据源名
  - `SOURCE_DATABASE`: 源端数据库名
  - `SOURCE_SCHEMA`: 源端 Schema
  - `SOURCE_TABLE`: 源端表名
  - `CONSTANT`: 常量值

- **字符串转换**:
  - `PREFIX`: 添加前缀 (如 `prd_tablename`)
  - `SUFFIX`: 添加后缀 (如 `tablename_backup`)
  - `REPLACE`: 字符串替换 (如 `dev_` → `prd_`)

- **使用场景**:
  - 保持原名映射
  - 添加前后缀
  - 字符串统一替换
  - 多张表写入一张表 (固定常量)

**示例代码**:
```typescript
// 创建库名前缀映射规则
const rule = createPrefixMappingRule("生产环境前缀", "SOURCE_DATABASE", "prd_");

// 应用规则
const targetPath = applyMappingRules(sourcePath, {
  sourceDatasourceName: "mysql-prod",
  databaseRule: rule,
});
// 源库 "orders" → 目标库 "prd_orders"
```

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

#### 5. 映射刷新与批量修改

**位置**: `yak-ops-ui/apps/web/service/data-sync/mapping-refresh.ts`

实现了智能映射刷新和批量修改：

- **刷新映射**: 根据当前规则重新生成所有目标名称
- **批量修改**: 对选中的表批量应用映射规则
- **冲突检测**: 自动检测多个源表映射到同一目标表
- **验证**: 验证所有映射的唯一性和完整性

```typescript
// 刷新所有映射
const refreshed = refreshMappingsWithRules(mappings, {
  sourceDatasourceName: "mysql-prod",
  databaseRule: dbRule,
  tableRule: tableRule,
  preserveExisting: true, // 保留已自定义的映射
});

// 检测冲突
const conflicts = detectMappingConflicts(refreshed);
// Map<targetKey, DataSyncTableMapping[]>
```

#### 6. UI 组件

**映射规则编辑对话框**: `name-mapping-rule-dialog.tsx`
- 快速模板选择
- 可视化规则配置
- 变量和转换管理
- 实时预览效果

**表结构编辑对话框**: `target-structure-editor-dialog.tsx`
- 字段列表管理
- 表类型选择
- 分区字段配置
- 分区赋值配置子对话框

**批量映射规则对话框**: `batch-mapping-rules-dialog.tsx`
- 批量应用库名/Schema/表名映射规则
- 多规则组合
- 预览应用效果

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
  
  // 新增: 映射规则引用
  databaseMappingRuleId?: string;
  tableMappingRuleId?: string;
  schemaMappingRuleId?: string;
  
  // 新增: 增量流水格式
  incrementalStreamFormat?: DataSyncIncrementalStreamFormat;
}
```

#### 在 `DataSyncCapabilities` 中的扩展

```typescript
interface DataSyncCapabilities {
  // ... 现有能力
  
  // 新增能力标识
  nameMappingRules: boolean;        // 支持名称映射规则
  targetStructureEditor: boolean;   // 支持目标表结构编辑
  partitionFields: boolean;         // 支持分区字段配置
  incrementalStream: boolean;       // 支持增量流水格式
}
```

### 📋 与 DataWorks 的对比

| 功能 | DataWorks | Yak Ops 实现 | 对齐状态 |
|------|-----------|-------------|----------|
| 目标表名映射规则 | ✓ 变量+转换 | ✓ 完全实现 | ✅ 对齐 |
| 目标库名映射规则 | ✓ 变量+转换 | ✓ 完全实现 | ✅ 对齐 |
| 目标 Schema 映射规则 | ✓ 变量+转换 | ✓ 完全实现 | ✅ 对齐 |
| 目标表结构编辑 | ✓ 字段/类型/分区 | ✓ 完全实现 | ✅ 对齐 |
| 分区字段配置 | ✓ 枚举值/事件时间 | ✓ 完全实现 | ✅ 对齐 |
| 刷新映射重新生成 | ✓ 根据规则重算 | ✓ 完全实现 | ✅ 对齐 |
| 增量流水表格式 | ✓ 定义明确 | ✓ 类型定义 | ✅ 对齐 |
| 批量修改规则 | ✓ 批量应用 | ✓ 完全实现 | ✅ 对齐 |
| 字段类型映射 | ✓ 全局规则 | ✓ 已有实现 | ✅ 对齐 |
| 字段赋值表达式 | ✓ 常量/变量/函数 | ✓ 已有实现 | ✅ 对齐 |

## 使用示例

### 1. 配置库名映射规则

```typescript
import { createPrefixMappingRule, applyNameMappingRule } from "@/service/data-sync";

// 创建添加前缀的规则
const rule = createPrefixMappingRule(
  "生产环境库名前缀",
  "SOURCE_DATABASE",
  "prd_"
);

// 应用规则
const result = applyNameMappingRule(rule, {
  sourceDatabase: "orders"
});
// 结果: "prd_orders"
```

### 2. 配置表名映射规则

```typescript
import { createDefaultTableMappingRule } from "@/service/data-sync";

// 默认规则: ${源库名}_${表名}
const rule = createDefaultTableMappingRule();

const result = applyNameMappingRule(rule, {
  sourceDatabase: "crm",
  sourceTable: "customers"
});
// 结果: "crm_customers"
```

### 3. 配置字符串替换规则

```typescript
import { createReplaceMappingRule } from "@/service/data-sync";

const rule = createReplaceMappingRule(
  "开发环境替换为生产",
  "SOURCE_DATABASE",
  "dev_",
  "prd_"
);

const result = applyNameMappingRule(rule, {
  sourceDatabase: "dev_analytics"
});
// 结果: "prd_analytics"
```

### 4. 配置目标表结构

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

### 5. 批量刷新映射

```typescript
import { refreshMappingsWithRules } from "@/service/data-sync";

const refreshed = refreshMappingsWithRules(mappings, {
  sourceDatasourceName: "mysql-prod",
  databaseRule: databaseRule,
  tableRule: tableRule,
  preserveExisting: false, // 重新生成所有映射
});
```

### 6. 检测映射冲突

```typescript
import { detectMappingConflicts } from "@/service/data-sync";

const conflicts = detectMappingConflicts(mappings);

if (conflicts.size > 0) {
  conflicts.forEach((group, targetKey) => {
    console.log(`目标 ${targetKey} 存在 ${group.length} 个源表冲突`);
    group.forEach(mapping => {
      console.log(`  - ${mapping.source.database}.${mapping.source.table}`);
    });
  });
}
```

## 后端集成要点

### 1. 持久化映射规则

映射规则应存储在任务定义的 `parameters` 中：

```json
{
  "sink": {
    "mappingRules": {
      "database": {
        "id": "rule-db-1",
        "name": "生产环境前缀",
        "pattern": "${0}",
        "variables": [...]
      },
      "table": {
        "id": "rule-table-1",
        "name": "库名_表名",
        "pattern": "${0}_${1}",
        "variables": [...]
      }
    },
    "tables": [
      {
        "database": "prd_orders",
        "table": "orders_detail",
        "databaseMappingRuleId": "rule-db-1",
        "tableMappingRuleId": "rule-table-1",
        "structure": {
          "columns": [...],
          "partitions": [...]
        }
      }
    ]
  }
}
```

### 2. Pipeline 转换

在 `PipelineYamlConverter` 中处理映射规则：

```java
// 应用映射规则生成目标路径
private TablePath applyMappingRule(
    TablePath source,
    NameMappingRule rule,
    Map<String, String> context
) {
    // 实现与前端一致的规则应用逻辑
}

// 生成分区配置
private List<PartitionField> buildPartitionFields(
    DataSyncTargetTableStructure structure
) {
    // 转换分区赋值配置为运行时分区逻辑
}
```

### 3. 目标表创建

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

### 4. 增量流水写入

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

### 2. 映射规则验证

- 确保规则生成的名称符合目标数据库命名规范
- 检测并提示映射冲突 (多源映射到同一目标)
- 验证常量值不为空
- 验证字符串替换模式有效

### 3. 表结构编辑

- 字段名和类型不能为空
- 字段名不能重复
- 分区字段名不能与普通字段重复
- 表类型更改可能影响写入模式兼容性

### 4. 向后兼容

- 已有任务的映射配置保持不变
- `databaseMappingRuleId` 等字段为可选
- 未配置规则时使用原有的直接映射逻辑

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
