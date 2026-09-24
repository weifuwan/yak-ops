# yak-ops-dao

`yak-ops-dao` owns shared database persistence infrastructure.

Call chain:

```text
Business
  ↓
Repository
  ↓
Mapper
  ↓
Database
```

## Current Scope

- MyBatis-Plus Repository base contract.
- MyBatis-Plus Repository base implementation.
- Shared persistence rules.
- Datasource Entity / Mapper / Repository persistence.

Datasource database persistence is owned here; Datasource Business keeps business-facing repository contracts and domain mapping only.

## Rules

Load [DAO_RULES.md](./DAO_RULES.md) for changes in this module.
