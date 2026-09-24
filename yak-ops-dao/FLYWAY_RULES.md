# Flyway Rules

Scope:
- `yak-ops-dao/src/main/resources/db/migration/yak-ops/**`
- `yak-ops-dao/src/main/java/io/yak/ops/dao/config/FlywayConfiguration.java`

Depends On:
- `/ARCHITECTURE.md`
- `./DAO_RULES.md`
- nearest capability rules for the schema being changed

Owns:
- the single Yak Ops Flyway runtime
- database schema history
- tables / columns / indexes
- migration ordering

## Ownership

All Yak Ops versioned SQL belongs to `yak-ops-dao`.

```text
yak-ops-dao
└── src/main/resources/db/migration/yak-ops
    ├── V1__...
    ├── V2__...
    └── ...
```

Security, Datasource, Boot, Core, SPI and Plugin modules must not create their own Flyway bean, Flyway history table or migration directory.

## Migration Contract

- 所有数据库结构和基础目录数据变更统一由 Flyway 管理。
- 已进入共享、测试或生产环境的 Migration 禁止修改，只能新增 Migration。
- 文件命名使用 `V{version}__{lower_snake_description}.sql`。
- 新 Migration 版本必须高于当前最大版本。
- 一个 Migration 只处理一个明确主题。
- 不为模块单独分配版本段；所有模块共享一条全局 Migration 序列。
- 需要 `${appName}` 的安全目录数据复用统一 Flyway placeholder。
- 破坏性 Schema 变更必须显式评估兼容、数据迁移和回滚风险。

## Compatibility

当前统一序列保留既有 Security / Boot Migration 版本和内容，避免破坏已有 `flyway_schema_history` 校验。

原 Datasource 独立 `V1` 接入统一序列时改为 `V2009__baseline_datasource.sql`；其建表语句本身使用 `IF NOT EXISTS`，兼容已执行旧 Datasource history 的数据库。

旧 `yak_datasource_schema_history` 不再写入，新版本只使用统一 Flyway history。

## Boundary

Flyway defines schema history. DAO owns persistence mechanics. Capability modules own business meaning but do not own migration runtime.
