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

## Flyway

Schema changes load [FLYWAY_RULES.md](./FLYWAY_RULES.md).

All Yak Ops versioned SQL lives in this module. Security, Datasource, Boot and Plugin modules must not own Flyway migrations or create their own Flyway bean/history table.

## Must


- Business code accesses persistence through Repository boundaries.
- Security Service must use `UserRepository`; direct `UserMapper` access outside DAO is not allowed.
- Reuse `BaseRepository` / `BaseRepositoryImpl` for ordinary single-table CRUD and pagination.
- Add domain-specific Repository methods only when the base contract cannot express the persistence semantics.
- Keep simple single-table queries in MyBatis-Plus Lambda APIs.
- Use Mapper XML for complex SQL when XML is clearer.
- Return `PageData` from persistence boundaries instead of leaking MyBatis `IPage` upward.
- Keep ID type generic; do not force the Yakable String-ID model onto existing Yak Ops Long-ID tables.
- Move concrete persistence into this module only as an explicit refactor, not as a side effect of unrelated work.

## Must Not

- Business Service or Controller depends directly on Mapper.
- Repeat `add / deleteById / update / queryById / queryList / queryCount / queryPage` as forwarding methods without additional semantics.
- Put HTTP DTO / VO contracts in DAO.
- Introduce `BaseEntity` until Yak Ops has one audited ID and audit-field contract that fits existing tables.
- Rebuild Datasource persistence in this foundation PR.
- Add Flyway migration directories outside `yak-ops-dao`.

## Boundary

DAO owns persistence mechanics. Business owns business rules and cross-table orchestration.
