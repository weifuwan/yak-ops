# DAO Rules

Scope:
- `yak-ops-dao/**`
- All concrete Yak Ops Repository / Mapper code

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- `/yak-ops-common/COMMON_RULES.md`

Owns:
- Shared persistence contracts
- Unified Flyway configuration and database schema history
- MyBatis-Plus Repository base capabilities
- All versioned SQL migrations under `src/main/resources/db/migration/yak-ops`
- Concrete product database access through DAO-owned repositories
- Security user persistence through `UserEntity`, `UserMapper` and `UserRepository`
- Workspace persistence through DAO-owned Workspace / WorkspaceMember Entity, Mapper and Repository
- User Preference persistence through DAO-owned Entity / Mapper / Repository
- Datasource persistence through DAO-owned Entity / Mapper / Repository and mapper XML
- 通用 Task Definition / append-only Version 属于 `dao.entity.task` 和 `dao.repository.task`。DATA_SYNC 表仅保留插件专属配置字段，`SyncDefinitionRepository` 将二者组合为现有业务查询投影，防止重复状态源。
- Data Sync Schedule / Execution / Attempt / Execution Event 仍由各自 DAO Entity / Repository 持有；Operations 聚合读模型同样由 DAO Mapper / Repository 承载。

## Flyway

Schema changes load [FLYWAY_RULES.md](./FLYWAY_RULES.md).

All Yak Ops versioned SQL lives in this module. Security, Datasource, Boot, Business and Plugin modules must not own Flyway migrations or create their own Flyway bean/history table.

## Entity

Entity changes load [ENTITY_RULES.md](./ENTITY_RULES.md).

Database table mapping objects use the `Entity` suffix and live under `io.yak.ops.dao.entity`. Entity mirrors persistence structure and never becomes an HTTP or Business output contract.

## Must

- Business code accesses persistence through Repository boundaries.
- Security Service must use `UserRepository`; direct `UserMapper` access outside DAO is not allowed.
- Reuse `BaseRepository / BaseRepositoryImpl` for ordinary single-table CRUD and pagination.
- All real table Entity types extend `BaseEntity` and use the shared String snowflake ID contract.
- Add domain-specific Repository methods only when the base contract cannot express the persistence semantics.
- Keep simple single-table queries in MyBatis-Plus Lambda APIs.
- Use Mapper XML for complex SQL when XML is clearer.
- Return `PageData` from persistence boundaries instead of leaking MyBatis `IPage` upward.
- Move concrete persistence into this module only as an explicit refactor, not as a side effect of unrelated work.
- Keep persistence enum storage aligned with the Flyway column contract.
- Keep Flyway schema, Entity fields and Repository queries synchronized.

## Must Not

- Business Service or Controller depends directly on Mapper.
- Repeat `add / deleteById / update / queryById / queryList / queryCount / queryPage` as forwarding methods without additional semantics.
- Put HTTP DTO / VO contracts in DAO.
- Introduce `PO`, `DO` or another duplicate table-mapping naming convention.
- Declare MyBatis table-mapping Entity classes in Common or Business modules.
- Introduce a second ID generator or switch individual tables back to auto-increment IDs.
- Add Flyway migration directories outside `yak-ops-dao`.
- Delete or rewrite applied migrations when removing a capability; remove obsolete schema/data through a new forward migration under FLYWAY_RULES.md.

## Boundary

DAO owns persistence mechanics. Business owns business rules and cross-table orchestration.
