# Data Sync Schema / Logical Table Contract

Status: Active — v1.2 Contract

Scope:

- Data Sync 产品级 Logical Table
- Logical Column / Primary Key / Schema Version
- Datasource Catalog → Logical Model 边界
- Logical Model → YakFlow Runtime Schema 边界
- 后续持久化、Target Planning、Auto Create Table 的稳定前置契约

## 1. Goal

v1.2 开始把“数据库里发现的一张物理表”与“Yak Ops 自己理解并保存的一张逻辑表”分开。

目标不是把 Source 的 CREATE TABLE SQL 保存下来，而是形成一个跨数据库、可以长期复用的产品级 Schema Model：

~~~text
Datasource Catalog
Physical Metadata
        ↓
LogicalTable
Product Metadata
        ↓
TableSchema
Runtime Contract
        ↓
Target Table Plan
Physical DDL
~~~

这层模型是后续 Auto Create Table、Schema Compatibility、Logical Modeling 与数仓建模的共同基础。

## 2. Ownership

### Datasource

Datasource 只负责发现真实数据库当前状态：

~~~text
database / schema / table
column name
native typeName
jdbcType
size / scale
nullable
ordinalPosition
primaryKey
remarks
~~~

Datasource Catalog 是实时物理元数据读取，不是 Yak Ops 的长期逻辑模型。

### Data Sync

Data Sync 拥有产品级 LogicalTable / LogicalColumn。

Logical Table 可以在后续 PR 中持久化为 Workspace-scoped 产品资源；它必须在没有实时数据库连接时仍能独立描述 Schema。

### YakFlow

YakFlow 继续只拥有数据平面运行契约：LogicalType、Column、TableSchema。

YakFlow 不拥有 Logical Table 的产品 ID、Workspace、Comment、Schema Version、Catalog 来源关系或建模生命周期。

### JDBC Connector / Target Planner

数据库原生目标类型与 DDL 由后续 Target Planner / JDBC Dialect 根据 Logical Model 生成。

禁止把 Source 的 MySQL / PostgreSQL / Oracle 原生类型名称直接复制成跨数据库目标建表规则。

## 3. Logical Table Contract

当前内存契约：

~~~text
LogicalTable
├── name
├── comment
├── schemaVersion
├── columns[]
└── primaryKeys[]
~~~

LogicalTable 本身是不可变 Schema Definition，不携带 DAO Entity 字段。

后续持久化资源至少需要在产品层拥有：

~~~text
logicalTableId
workspaceId
name
comment
schemaVersion
columns
primaryKeys
created / updated audit fields
~~~

logicalTableId / workspaceId / audit fields 属于资源与持久化层，不进入 YakFlow Runtime Schema。

Logical Table 必须是自包含 Schema Snapshot。即使原 Datasource 或 Source Table 后续不可用，已经保存的 Logical Table 仍能描述当时确认的结构。

## 4. Logical Column Contract

当前字段契约：

~~~text
LogicalColumn
├── name
├── dataType: LogicalType
├── nullable
├── length
└── comment
~~~

规则：

- 字段名称保留来源标识符原始大小写，不在 Logical Model 中统一 lower-case。
- 类型唯一标准是 YakFlow LogicalType，不再维护第二套 Data Sync 类型枚举。
- STRING / BINARY 可以保存 length；未知容量使用 null。
- DECIMAL 的 precision / scale 由 YakDecimalType 自己承载，不塞进通用 length。
- comment 是产品元数据，不进入 Runtime Schema。
- Primary Key 在 Table 层使用有序字段名列表表达，从而保留复合主键顺序。
- Primary Key 必须引用当前 Logical Table 中真实存在的字段。

当前 Logical Type 家族继续复用：

~~~text
BOOLEAN
TINYINT
SMALLINT
INTEGER
BIGINT
FLOAT
DOUBLE
DECIMAL
STRING
BINARY
DATE
TIME
TIMESTAMP
TIMESTAMP_WITH_TIME_ZONE
~~~

本 PR 不引入 ARRAY / MAP / ROW 等复合类型。

## 5. What Does Not Belong in Logical Table

以下内容不是 Logical Schema 的 canonical identity：

- Source JDBC typeName。
- java.sql.Types 编码。
- MySQL / PostgreSQL / Oracle 专属 DDL 片段。
- Target database / schema / table physical path。
- Target dialect。
- Runtime connection / credential。
- Debezium offset / schema history。
- Execution / Attempt identity。

Source 的 typeName / jdbcType / size / scale 可以用于导入、兼容分析和诊断，但不能成为跨数据库逻辑模型的唯一类型表示。

当前 Datasource Catalog 没有稳定暴露 Column Default / Index / Foreign Key，因此 PR1 不把这些字段伪造成 Logical Table Contract。后续只有在来源、语义和跨数据库规则明确后才能扩展。

## 6. Schema Version Contract

schemaVersion 从 1 开始，并与以下版本完全独立：

~~~text
Product Version        1.2.0
Task definitionVersion
LogicalTable schemaVersion
~~~

它们不能互相驱动。

Logical Table 的结构发生变化时推进 schemaVersion：

- Column add / remove。
- Column order 改变。
- Column name 改变。
- Logical Type / precision / scale / length 改变。
- nullable 改变。
- Primary Key 集合或顺序改变。

仅修改表 / 字段 comment 等展示元数据不推进 schemaVersion。

后续如果提供 Catalog Refresh：

~~~text
Saved LogicalTable
        +
Fresh Datasource Catalog
        ↓
Schema Diff
        ↓
No structural change
→ keep schemaVersion

Structural change accepted by user/product rule
→ schemaVersion + 1
~~~

Catalog Refresh 不能静默覆盖一个已保存且被 Task / Model 引用的 Schema Snapshot。

## 7. Runtime Projection

产品 Logical Table 可以投影成 YakFlow Runtime Schema：

~~~text
LogicalTable
        ↓
TableSchema
~~~

必须保留 Column order、Column name、LogicalType、nullable、STRING / BINARY capacity 与 Primary Key order。

不会进入 Runtime：Logical Table name、comment、schemaVersion、product resource identity、workspace、audit fields。

当前代码通过 LogicalTable#toRuntimeSchema() 固定这条最小投影规则。

## 8. Source Import Boundary

PR2 已实现 Source Metadata Introspection + Logical Type Normalization：

~~~text
Datasource Catalog exact table metadata
        +
Datasource Catalog columns
        ↓
schema.catalog.SourceTableIntrospector
        ↓
LogicalTableNormalizer
        ↓
JdbcSchemaMapper / LogicalType
        ↓
LogicalTable
~~~

Datasource Catalog 现在提供精确 `findTable(DataSourceTablePath)`，DataSourceService 内部提供 `queryCatalogTable(...)`，Data Sync 不再通过 table keyword 搜索结果猜测表备注或对象身份。

字段归一规则：

- 字段按 Catalog `ordinalPosition` 恢复稳定顺序。
- JDBC `typeName / jdbcType / size / scale` 只作为物理输入。
- Logical Type 唯一通过现有 `JdbcSchemaMapper` 归一，不在 Data Sync 再写 JDBC type switch。
- STRING / BINARY capacity 继续由 Column length 表达。
- DECIMAL precision / scale 继续由 YakDecimalType 表达。
- table remarks / column remarks 作为初始 comment，空白备注归一为 null。
- Catalog 额外保留 JDBC `KEY_SEQ` 为 `primaryKeyPosition`，复合主键按 KEY_SEQ 顺序进入 LogicalTable，不按字段物理顺序猜测。

禁止：

- 按数据库产品名维护新的逻辑类型枚举。
- 用 Source native typeName 直接决定 Target DDL。
- Source introspection 读取或暴露 Runtime credential。
- Catalog import 阶段直接持久化或执行 Target DDL。

当前没有新增 Logical Table HTTP API。现有 Catalog Column 响应增加 `primaryKeyPosition`，用于保留复合主键顺序。

## 9. Target Planning Boundary

PR3 已实现 Target Table Planner + MySQL / PostgreSQL / Oracle JDBC Dialect DDL planning：

~~~text
LogicalTable
      +
Target Datasource Type
      +
Target Table Path
        ↓
schema.target.TargetTablePlanner
        ↓
JdbcDialect
        ↓
TargetTablePlan
├── native column types
├── primary key order
├── warnings
├── unsupported reasons
└── CREATE TABLE SQL
~~~

职责边界：

- Data Sync `TargetTablePlanner` 负责产品级 Plan、schemaVersion、comment、warning / unsupported 聚合。
- YakFlow JDBC `JdbcDialect` 负责 identifier quote、target path qualification、Logical Type → Native Type 和 CREATE TABLE SQL。
- Logical Table 不保存最终 DDL，避免产品元数据绑定某个数据库方言。
- Planner 不连接数据库、不判断目标表是否存在、不执行 DDL。
- 任意阻塞型不兼容存在时，`TargetTablePlan.supported=false` 且 `createTableSql=null`，不能生成“部分可用”DDL。

当前核心映射：

| Logical Type | MySQL | PostgreSQL | Oracle |
| --- | --- | --- | --- |
| BOOLEAN | BOOLEAN | BOOLEAN | NUMBER(1) |
| TINYINT | TINYINT | SMALLINT | NUMBER(3) |
| SMALLINT | SMALLINT | SMALLINT | NUMBER(5) |
| INTEGER | INT | INTEGER | NUMBER(10) |
| BIGINT | BIGINT | BIGINT | NUMBER(19) |
| FLOAT | FLOAT | REAL | BINARY_FLOAT |
| DOUBLE | DOUBLE | DOUBLE PRECISION | BINARY_DOUBLE |
| DECIMAL(p,s) | DECIMAL(p,s) | NUMERIC(p,s) | NUMBER(p,s) |
| STRING(n) | VARCHAR(n) | VARCHAR(n) | VARCHAR2(n CHAR) |
| BINARY(n) | VARBINARY(n) | BYTEA | RAW(n) |
| DATE | DATE | DATE | DATE |
| TIME | TIME(6) | TIME | unsupported |
| TIMESTAMP | DATETIME(6) | TIMESTAMP | TIMESTAMP(6) |
| TIMESTAMP_WITH_TIME_ZONE | unsupported | TIMESTAMP WITH TIME ZONE | TIMESTAMP(6) WITH TIME ZONE |

容量 / 精度安全规则：

- MySQL DECIMAL precision 最大 65、scale 最大 30；Oracle NUMBER precision 最大 38，超过时直接 unsupported。
- DECIMAL precision / scale 不完整时使用数据库未限定精度类型并给 warning，不编造参数。
- MySQL 大容量或未知 STRING / BINARY 分别安全放宽为 LONGTEXT / LONGBLOB。
- PostgreSQL 未知 STRING 放宽为 TEXT，BINARY 使用 BYTEA。
- Oracle 超过 VARCHAR2(4000 CHAR) / RAW(2000) 或未知容量时放宽为 CLOB / BLOB。
- 由 LOB 类宽化得到的 LONGTEXT / LONGBLOB / CLOB / BLOB 在当前 Planner 中不能直接作为目标主键；该场景为 blocking unsupported。
- MySQL 无法保留 TIMESTAMP_WITH_TIME_ZONE 语义；Oracle 没有独立 TIME 列类型，这两类当前直接 unsupported。

Table / Column comment 继续保留在 TargetTablePlan 产品元数据中；自动建表 DDL 同步保留这些注释：MySQL 内联到 CREATE TABLE，PostgreSQL / Oracle 在 CREATE TABLE 后追加 COMMENT ON TABLE / COLUMN。

## 10. Auto Create Table Runtime

PR4 在 PR3 的 TargetTablePlan 之上增加受控 Runtime Preflight：

~~~text
Execution Attempt
      ↓
TargetTablePreflight
      ↓
target exists?
  ├─ yes
  │   ↓
  │ schema.target.TargetSchemaCompatibility
  │   ↓
  │ compatible → continue
  │ incompatible → fail
  │
  └─ no
      ↓
    autoCreateTable?
      ├─ false → TARGET_TABLE_NOT_FOUND
      └─ true
          ↓
        TargetTablePlanner
          ↓
        supported?
          ├─ no → TARGET_SCHEMA_INCOMPATIBLE
          └─ yes
              ↓
            JdbcTargetTableProvisioner
              ↓
            CREATE TABLE
              ↓
            re-introspect Catalog
              ↓
            TargetSchemaCompatibility
~~~

安全规则：

- `autoCreateTable` 属于 Task Definition，默认 `false`；旧任务升级后保持原有“目标表必须存在”行为。
- 修改 `autoCreateTable` 推进 Task `definitionVersion`，Execution 创建时冻结进 `definitionSnapshot`；Retry / Auto Recovery 不读取 Task 当前值覆盖历史 Execution。
- 保存 / 发布 / 运行都重新检查真实外部 Catalog，不依赖前端预览结果。
- 目标表已经存在时永远不执行 CREATE、DROP 或 ALTER，只做兼容性检查。
- 目标表不存在且开启自动建表时，只执行 YakFlow JDBC Dialect 从受控 TablePath + TableSchema 生成的 CREATE TABLE，不接受任意用户 SQL。
- CREATE TABLE 后必须重新 introspect 目标 Catalog，再做兼容性与主键校验；不能因为 DDL 执行成功就直接相信计划。
- 并发建表时，如果本次 CREATE 失败但随后精确 Catalog 已发现目标表，按并发创建处理并继续重新校验，而不是盲目重试 DDL。
- Target Schema 必须包含全部 Source 同名字段并满足 JdbcSchemaCompatibility。
- Source nullable 字段不能写入 Target NOT NULL 字段。
- Target 多余字段只有在 nullable 时允许；当前 Catalog 尚未稳定暴露 COLUMN DEFAULT，因此多余的 NOT NULL 字段按不兼容保守拒绝。
- REALTIME 继续要求 Source / Target 主键集合一致；UPSERT 继续要求 Target 有主键且 Source 包含全部目标主键。
- 自动创建新表时保留 LogicalTable / LogicalColumn comment；只对本次新建表执行受控 Comment DDL，不对已存在目标表做 COMMENT / ALTER 同步。
- 仍不提供 INDEX、FOREIGN KEY、ALTER、DROP、DDL Sync 或 Automatic Schema Evolution。

Task persistence 已在 v1.2 Release Freeze 收口到唯一 Release Migration：

~~~text
V3__v1_2_0.sql
~~~

该 Migration 按原 Draft 顺序依次增加 `yak_ops_data_sync_task.auto_create_table` 与可空 `mapping_config`。前者默认 0；后者 NULL 保持旧任务的隐式同名映射语义。原 `V3__data_sync_auto_create_table.sql` / `V4__data_sync_column_mapping.sql` 仅属于未发布、可重建的开发历史，已从 Release 候选链删除。

`V3__v1_2_0.sql` 当前已进入 v1.2 Migration Freeze，但尚未正式发布；发现 Release Blocker 需要修改时必须重新打开 Migration Freeze 并重新 Review。V1 / V2 继续保持永久冻结。

## 10.1 Mapping-Aware Schema Projection

Task Column Mapping 在进入 Target Compatibility / Planning / Runtime 前先投影为两张位置对齐的 Logical Schema：

~~~text
Source LogicalTable
      +
Task mapping.columns
        ↓
schema.mapping.SchemaMappingResolver
├── Source Read LogicalTable
│   └── 来源字段名，按 Mapping 顺序 / 子集
└── Target LogicalTable
    └── 相同类型 / nullable / capacity，在同一位置使用目标字段名
~~~

规则：

- `mapping=null` 继续生成全字段大小写不敏感同名映射。
- 显式 Mapping 的数组顺序就是 Runtime YakRow 的字段位置顺序。
- Source 读取只包含被映射字段，因此字段子集不需要 Transform。
- Target Compatibility 与 Auto Create Planner 只看到映射后的目标字段名。
- Source Primary Key 只有在**完整复合主键全部被映射**时才进入投影，Target LogicalTable 的 PK 名称按 Mapping 重命名；部分映射复合主键不会降级成新的单列 / 子集主键。
- REALTIME 必须映射全部 Source PK；Existing Target 的真实 PK 集合必须等于映射后的 PK 集合。
- UPSERT Auto Create 必须映射全部 Source PK，避免先创建无 PK 目标表后再失败。
- Mapping 不改变 Logical Type，不承担 CAST / expression / computed column。

## 11. Persistence Boundary

Logical Table persistence 仍未实现。

后续持久化实现必须满足：

- Workspace-scoped。
- Logical Table 使用稳定产品 ID。
- Schema 内容是 Yak Ops Source of Truth，不依赖查询时重新连接 Source。
- 不建立到 Datasource 的数据库物理外键；Datasource 删除不能让历史 Logical Schema 失去可读性。
- Catalog 来源关系如果保存，只能作为 provenance / refresh hint，不作为 Logical Schema 唯一身份。
- 不把 connectionJson、password、JDBC URL、Token 等 Secret 写入 Logical Table。
- 已冻结到 Task definitionSnapshot 的 Schema 不能因为 Logical Table 后续修改而改变历史 Execution。

具体 Entity / Mapper / Migration 由后续实现 PR 决定，不能在 Contract 阶段提前锁死物理表设计。

## 12. Current v1.2 Boundary

PR1 完成后只有 Schema / Logical Table Contract。

~~~text
Logical Table Persistence = NOT IMPLEMENTED
Source Metadata Introspection = IMPLEMENTED
Logical Type Normalization = IMPLEMENTED
Catalog Refresh / Diff = NOT IMPLEMENTED
Target Table Planner = IMPLEMENTED
MySQL / PostgreSQL / Oracle Target Dialect = IMPLEMENTED
CREATE TABLE + COMMENT DDL Planning = IMPLEMENTED
Auto Create Table Runtime = IMPLEMENTED
Runtime Schema Compatibility Preflight = IMPLEMENTED
Auto Create Table Preview API = IMPLEMENTED
Schema Preview UI = IMPLEMENTED
Task Column Mapping Contract / Persistence = IMPLEMENTED
Mapping-Aware Schema Resolution / Runtime = IMPLEMENTED
DDL Sync = NOT IMPLEMENTED
Automatic Schema Evolution = NOT IMPLEMENTED
Multi-table Task = NOT IMPLEMENTED
~~~

OFFLINE / REALTIME Task 默认仍要求目标表预先存在；只有 Task Definition 显式 `autoCreateTable=true` 且 Target Plan supported 时，Runtime 才允许创建缺失目标表。

## 13. Verification

Contract test 至少验证：

- Logical Table 可以稳定投影为 TableSchema。
- Composite Primary Key 顺序保持。
- Primary Key 不能引用不存在字段。
- Logical Column 名称不能重复。
- capacity 只允许出现在 STRING / BINARY。

PR2 通过 LogicalTableNormalizer / SourceTableIntrospector Contract Test 验证 Catalog Import 的内存归一行为。PR3 通过 TargetTablePlanner / JdbcCreateTableDialectTest 验证跨库类型规划。PR4 通过 TargetSchemaCompatibility / TargetTablePreflight Contract Test 验证存在、缺失、自动创建与不兼容分支，并在 OfflineSyncJdbcAcceptanceIT 中通过 JdbcTargetTableProvisioner 对 MySQL / PostgreSQL / Oracle 实际创建目标表。自动建表 Comment DDL 同样由 JdbcDialect 规划并在真实三库验收中读回验证。PR5 将后端 Preview Contract 暴露到 Task Editor：显式 Auto Create Switch、缺失目标表名输入、Target existence、field mapping、warning / unsupported 与只读完整 DDL；前端不复制 JDBC 类型或兼容算法。Logical Table persistence 仍属于后续能力。
