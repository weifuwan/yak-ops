# Common Rules

Scope:
- `yak-ops-common/**`

Depends On:
- `/JAVA_RULES.md`

Owns:
- Datasource shared DTO / VO
- Datasource enums and constants
- Security user/login DTO / VO / Enum contracts
- unified Result / ErrorCode / PageData contracts
- shared business exception hierarchy under `io.yak.ops.common.exception`
- small shared infrastructure with no better owner

## Namespace

All shared Common contracts use the `io.yak.ops.common` namespace. Shared exception types live under `io.yak.ops.common.exception`.

Do not recreate the legacy `io.yak.ops.common` compatibility package.

## Enum Contracts

Common 中带字段的枚举统一保持不可变契约：

- 使用 `@Getter` + `@RequiredArgsConstructor`，不手写纯样板 constructor / getter。
- 枚举字段使用 `private final`。
- ErrorCode 枚举统一使用 `code` / `message`。
- 普通业务枚举的展示文案统一使用 `displayName`；业务标识字段使用明确名称，例如 `code`。
- 枚举常量本身表达 Java 语义，数据库存储值属于 Persistence Contract。
- 只有数据库列、Flyway、Entity 和查询逻辑在同一个变更中完成迁移时，才允许给持久化字段增加 `@EnumValue` 或等价映射。

禁止为了减少几行代码提前改变枚举的数据库存储语义。

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
