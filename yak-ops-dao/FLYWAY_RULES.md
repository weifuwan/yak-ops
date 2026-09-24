# Flyway Rules

Scope:
- `yak-ops-dao/src/main/resources/db/migration/yak-ops/**`
- `yak-ops-dao/src/main/java/io/yak/ops/dao/config/FlywayConfiguration.java`

Depends On:
- `/ARCHITECTURE.md`
- `./DAO_RULES.md`
- `./ENTITY_RULES.md`
- nearest capability rules for the schema being changed

Owns:
- Yak Ops 单一数据库 Schema 历史
- 表 / 字段 / 索引定义
- Migration 顺序
- Schema 与 Entity 的持久化契约

## Ownership

所有 Yak Ops Schema 统一由 `yak-ops-dao` 管理。

```text
yak-ops-dao
└── src/main/resources/db/migration/yak-ops
    ├── V1__baseline.sql
    ├── V2__...
    └── ...
```

Security、Datasource、Boot、Business、Core、SPI 和 Plugin 模块不得创建自己的 Flyway Bean、history table 或 migration 目录。

## Baseline Mode

Yak Ops 当前仍处于可整体重建数据库的早期阶段。

因此当前允许把历史 Migration 收口成一个最终 `V1__baseline.sql`，前提是：
- 所有本地 / 开发数据库都允许删除后重新初始化。
- 不需要兼容旧的 `flyway_schema_history`。
- Baseline 只描述当前真实产品需要的最终 Schema，不保留已删除模块、历史页面、历史权限或兼容结构。

最终 V1 进入共享测试或生产环境后：
- 禁止修改 V1。
- 后续结构变化只能新增 V2、V3……
- 已发布 Migration 禁止重写、重排或删除。

## Migration Contract

- 所有数据库结构变化统一由 Flyway 管理。
- 文件命名使用 `V{version}__{lower_snake_description}.sql`。
- 一个 Migration 只处理一个明确变更主题。
- 新版本必须高于当前最大版本。
- 禁止为不同模块分配 V1000 / V2000 等版本段；所有能力共享一条连续版本序列。
- 禁止用 `IF EXISTS / IF NOT EXISTS` 掩盖异常 Schema。
- 删除表 / 字段、修改字段类型等破坏性变化必须明确评估数据迁移与回滚风险。
- Flyway 只保留当前产品真正需要的 Schema 和必要基础数据，不保存已经删除能力的历史目录数据。

## Table Contract

Must:
- 表名使用 lower_snake_case。
- Yak Ops 自有表使用 `yak_` 前缀，并按能力继续细分，例如 `yak_ops_`、`yak_security_`。
- 当前 Yak Ops 主键统一使用 `BIGINT AUTO_INCREMENT`，Java 使用 `Long`。
- 主键字段统一命名为 `id`。
- 使用 InnoDB。
- 字符集统一 `utf8mb4`。
- 排序规则统一 `utf8mb4_unicode_ci`，兼容当前 MariaDB 10.6 运行环境。
- 每张表必须有中文 COMMENT。
- 每个业务字段必须有中文 COMMENT。
- 时间字段统一使用 `DATETIME(3)`。
- 常规审计时间字段统一命名为 `create_time / update_time`。
- 表结构必须与对应 Entity 的持久化字段保持一致。
- 只有真实查询需要的字段才创建索引。

主键：

```sql
id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'
```

表配置：

```sql
ENGINE=InnoDB
DEFAULT CHARACTER SET=utf8mb4
COLLATE=utf8mb4_unicode_ci
COMMENT='业务表中文说明';
```

## Column Contract

- 字段名使用 lower_snake_case。
- `VARCHAR(n)` 必须来自真实业务上限，禁止无脑使用 255。
- 名称通常按 64 / 128 等真实上限选择。
- URL、JSON、密码哈希等字段根据真实数据长度选择 VARCHAR / TEXT / LONGTEXT。
- 必填字段使用 `NOT NULL`；只有真实可选字段允许 `NULL`。
- 禁止为了避免判空设置无意义默认值。
- 默认值必须表达真实业务默认状态。
- 状态 / 类型字段的 COMMENT 必须写清取值语义。
- 敏感字段 COMMENT 要明确其用途，但不得包含真实凭证示例。
- Java 枚举的存储形式必须与 SQL 类型匹配；现有字符串枚举不得在无迁移方案时静默改成数字。

## Relationship / Index Contract

- 禁止数据库物理外键。
- 表之间只保存关联 ID，关系完整性由 Business / Service 事务维护。
- 真实用于 `WHERE / ORDER BY` 的字段必须评估索引。
- 联合索引按真实查询设计：等值字段在前，范围 / 排序字段在后。
- 联合索引已覆盖的前缀字段不重复创建无意义索引。
- 唯一索引命名 `uk_<table_without_yak_prefix>_<field>`。
- 普通索引命名 `idx_<table_without_yak_prefix>_<field>`。
- 低区分度状态字段只有存在真实筛选需求时才单独建索引。

## Entity Sync

任何 Schema 变化都必须同时检查：
- 对应 Entity 字段。
- Entity 字段 Java 类型。
- Entity / 字段 JavaDoc。
- Mapper / Repository 查询。
- 枚举存储方式。

Flyway COMMENT 与 Entity 字段注释表达的业务语义必须一致。

## Boundary

```text
Flyway
  ↓ defines
Schema
  ↑ mirrors
Entity
  ↑ consumed by
Repository
  ↑
Business / Service
```

Flyway 定义数据库结构；Entity 镜像持久化结构；Repository 消费结构；Business / Service 拥有业务语义。
