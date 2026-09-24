# yak-ops-dao

`yak-ops-dao` owns Yak Ops persistence infrastructure and the single database schema history.

Call chain:

```text
Business / Service
  ↓
Repository
  ↓
Mapper / Entity
  ↓
Database
```

## Current Scope

- Shared MyBatis-Plus Repository base contract and implementation.
- Security user persistence.
- Datasource persistence.
- Unified Flyway schema and migration history.
- Entity / Flyway / Repository persistence rules.

## Rules

Changes in this module load:
- [DAO_RULES.md](./DAO_RULES.md)
- Schema changes: [FLYWAY_RULES.md](./FLYWAY_RULES.md)
- Entity changes: [ENTITY_RULES.md](./ENTITY_RULES.md)
