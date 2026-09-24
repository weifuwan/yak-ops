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

Concrete Datasource persistence is still owned by `yak-ops-business-datasource` in this PR and should be migrated separately instead of being mixed into the module bootstrap.

## Rules

Load [DAO_RULES.md](./DAO_RULES.md) for changes in this module.
