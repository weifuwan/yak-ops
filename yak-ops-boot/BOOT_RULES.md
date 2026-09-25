# Boot Rules

Scope:
- `yak-ops-boot/**`

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`

Owns:
- Spring Boot application entry and final runtime assembly
- HTTP Controller / ControllerAdvice
- application-wide framework configuration

## Runtime Contract

Yak Ops currently uses one application database runtime.

spring.datasource → DataSource → default TransactionManager → MyBatis-Plus SqlSessionFactory → SqlSessionTemplate → DAO Mapper

Spring Boot and the MyBatis-Plus Starter own the standard bean creation. Boot adds only Yak Ops-specific policies and plugins.

## Must

- Use Spring Boot auto-configuration for the single DataSource and transaction manager.
- Use MyBatis-Plus Starter auto-configuration for the single SqlSessionFactory and SqlSessionTemplate.
- Scan DAO mappers from the shared `io.yak.ops.dao.mapper` root.
- Keep one MyBatis-Plus interceptor chain.
- Scope Security `app_name` tenant behavior to Security tables instead of creating a second MyBatis runtime.
- Prefer framework customizers/properties over replacing framework beans.
- Keep `application.yml` limited to capabilities that exist in the current product.
- Keep one OpenAPI document unless a real external contract requires multiple groups.

## Must Not

- Create capability-specific aliases such as `opsDataSource`, `yakSecurityDataSource`, `opsResourceTransactionManager` or `offlineSyncSqlSessionFactory`.
- Create separate SqlSessionFactory / SqlSessionTemplate instances for Datasource and Security when they use the same database.
- Hard-code MyBatis pagination dialect when the runtime can resolve it from the configured DataSource.
- Define a `@Primary ObjectMapper` that only rebuilds Spring Boot's default mapper.
- Keep startup-only logging helpers solely to print framework URLs.
- Keep configuration blocks for removed capabilities.
- Recreate deleted Resource, Sync, Schedule or Quartz runtime wiring as compatibility configuration.

## Transaction Boundary

Business and Security code use the default transaction manager with `@Transactional(rollbackFor = Exception.class)`.

A named transaction manager requires a genuinely separate database runtime, not a capability name.
