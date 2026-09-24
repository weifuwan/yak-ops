# Common Rules

Scope:
- `yak-ops-common/**`

Depends On:
- `/JAVA_RULES.md`

Owns:
- Datasource shared DTO / VO / PO
- Datasource enums and constants
- unified Result / ErrorCode / BusinessException / PageData contracts
- small shared infrastructure with no better owner

## Compatibility Namespace

The package `io.yak.framework.common` is physically owned by `yak-ops-common`.

The package name is a migration residue only. Yak Ops no longer depends on external Yak Common or Yak Security artifacts.

Do not create a second copy under `io.yak.ops.common` until a deliberate namespace migration is designed.

## Must

- unified Result / ErrorCode / BusinessException / PageData Contract only has one implementation.
- public shared objects remain behavior-free.
- existing MyBatis shared configuration is reused where appropriate.

## Must Not

- depend on external yak-framework modules.
- put Datasource or Security business orchestration in Common.
- use Common as a miscellaneous dumping ground.
- recreate deleted tests or CI.

## Boundary

Common stores stable shared data structures and small infrastructure only.
