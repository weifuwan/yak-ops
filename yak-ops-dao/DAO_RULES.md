# DAO Rules

Scope:
- `yak-ops-dao/**`
- Concrete Repository / Mapper code after it is migrated into this module

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- `/yak-ops-common/COMMON_RULES.md`

Owns:
- Shared persistence contracts
- Unified Flyway configuration and database schema history
- MyBatis-Plus Repository base capabilities
- All versioned SQL migrations under `src/main/resources/db/migration/yak-ops`
- Concrete database access after a domain is explicitly migrated here
- Security user persistence through `UserEntity`, `UserMapper` and `UserRepository`
- Datasource persistence through DAO-owned Entity / Mapper / Repository and mapper XML

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
- Add domain-specific Repository methods only when the base contract cannot express the persistence semantics.
- Keep simple single-table queries in MyBatis-Plus Lambda APIs.
- Use Mapper XML for complex SQL when XML is clearer.
- Return `PageData` from persistence boundaries instead of leaking MyBatis `IPage` upward.
- Keep current Yak Ops Long auto-increment ID contract unless a dedicated migration changes it.
- Move concrete persistence into this module only as an explicit refactor, not as a side effect of unrelated work.
- Keep persistence enum storage aligned with the Flyway column contract.
- Keep Flyway schema, Entity fields and Repository queries synchronized.

## Must Not

- Business Service or Controller depends directly on Mapper.
- Repeat `add / deleteById / update / queryById / queryList / queryCount / queryPage` as forwarding methods without additional semantics.
- Put HTTP DTO / VO contracts in DAO.
- Introduce `PO`, `DO` or another duplicate table-mapping naming convention.
- Declare MyBatis table-mapping Entity classes in Common or Business modules.
- Introduce `BaseEntity` before a real shared audit-field contract exists.
- Add Flyway migration directories outside `yak-ops-dao`.
- Keep tables or migrations for product capabilities that have already been deleted.

## Boundary

DAO owns persistence mechanics. Business owns business rules and cross-table orchestration.
