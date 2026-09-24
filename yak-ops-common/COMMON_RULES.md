# Common Rules

Scope:
- `yak-ops-common/**`

Depends On:
- `/JAVA_RULES.md`

Owns:
- Datasource shared DTO / VO / PO
- Datasource enums and constants
- Security user/login DTO / VO / Enum contracts
- unified Result / ErrorCode / PageData contracts
- shared business exception hierarchy under `io.yak.ops.common.exception`
- small shared infrastructure with no better owner

## Namespace

All shared Common contracts use the `io.yak.ops.common` namespace. Shared exception types live under `io.yak.ops.common.exception`.

Do not recreate the legacy `io.yak.ops.common` compatibility package.

## Must

- unified Result / ErrorCode / BusinessException / PageData Contract only has one implementation.
- `BusinessException` and cross-capability exception types stay independent from HTTP/Spring Web.
- public shared objects remain behavior-free.
- Security shared contracts use the `io.yak.ops.common` namespace; do not recreate copies under `yak-ops-security`.
- existing MyBatis shared configuration is reused where appropriate.

## Must Not

- depend on external yak-framework modules.
- put Datasource or Security business orchestration in Common.
- put HTTP status or ControllerAdvice behavior into Common.
- use Common as a miscellaneous dumping ground.
- recreate deleted tests or CI.

## Boundary

Common stores stable shared data structures, shared exception contracts and small infrastructure only. HTTP exception mapping belongs to `yak-ops-boot`.
