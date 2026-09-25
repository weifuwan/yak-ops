# Yak Ops Architecture

Status: Active

Scope:
- Current Yak Ops Datasource-only product architecture
- Supporting user/login/security capability
- Module ownership and dependency direction

Depends On:
- `docs/engineering-context-model.md`

## Principle

Yak Ops is currently a Datasource product.

User/Login/Security is a supporting platform capability required to access the product.

Architecture follows current ownership, not historical modules and not a future platform plan.

HTTP is an application boundary. All Controller ownership belongs to `yak-ops-boot`; capability modules expose business capability to Boot and never own HTTP entry classes.

Application runtime infrastructure is also a Boot boundary. DataSource/MyBatis-Plus runtime policy, OpenAPI/Swagger and MVC interceptor/filter registration belong to `yak-ops-boot`. Capability modules provide behavior and persistence contracts without assembling the final Spring application. Boot prefers Spring Boot/Starter auto-configuration over manually recreating framework beans.

## Current Modules

### `yak-ops-common`

Owns shared data contracts for Datasource and Security, plus the unified Result / ErrorCode / PageData contracts and shared exception hierarchy under `io.yak.ops.common.exception`. Security user/login DTO, VO, enum and shared exception contracts live here instead of inside the Security runtime module.

### `yak-ops-security`

Owns user management, login/logout/current identity, HttpSession authentication state, authentication policy and the authentication interceptor implementation. It does not own RBAC administration, project authorization, messaging or notification runtime.

Security does not own Controller, ControllerAdvice, OpenAPI configuration, connection-pool/MyBatis assembly or MVC interceptor registration. Boot exposes and wires the current Security HTTP capability by calling Security-owned services and registering Security-owned behavior.

Security production code was migrated from `yak-framework/yak-security`.

Security business/runtime code uses the `io.yak.ops.security` product namespace. Shared DTO / VO / enum contracts live in `io.yak.ops.common`, while user persistence is owned by `yak-ops-dao`.

### `yak-ops-dao`

Owns shared database persistence infrastructure:

- MyBatis-Plus Repository base contract
- MyBatis-Plus Repository base implementation
- persistence rules shared by concrete DAO code
- the single Flyway configuration and schema history for all Yak Ops modules
- all versioned SQL under `yak-ops-dao/src/main/resources/db/migration/yak-ops`

Concrete Security user persistence and Datasource persistence are owned here.

BusinessImpl may use DAO-owned Entity/Repository internally. Entity and DAO Model do not cross the Business boundary into Boot.

DAO owns persistence and schema migration, not final application DataSource/MyBatis runtime assembly. That assembly belongs to Boot.

### `yak-ops-spi`

Reserved minimal extension boundary.

### `yak-ops-core`

Reserved empty module.

### `yak-ops-business`

Owns the application Service Layer. Each stable capability exposes `XxxBusiness` interfaces and keeps Spring implementation, transactions, validation and DAO/Plugin orchestration in `impl/XxxBusinessImpl`.

Boot depends on Business interfaces. Business public contracts use shared DTO / VO types and do not expose DAO Entity, Mapper, Repository Query or concrete Plugin implementation details.

Detailed rules are defined in `yak-ops-business/BUSINESS_RULES.md`.

### `yak-ops-business/yak-ops-business-datasource`

Owns only the current Datasource product behavior:

- datasource management and connection testing
- datasource Catalog metadata browsing
- datasource plugin discovery, connection parsing and secret handling

The module intentionally does not own SQL execution, SQL audit, a duplicate Domain layer or a Gateway adapter layer.

Datasource follows the common Business Layer contract and may use DAO persistence and stable Datasource Plugin SPI only behind Business implementations.

Datasource does not own Controller, ControllerAdvice, connection-pool assembly or MyBatis runtime configuration. Boot exposes Datasource HTTP APIs and supplies application infrastructure.

### `yak-ops-plugins/yak-ops-plugin-datasource`

Owns Datasource provider contracts and implementations.

The active plugin surface is limited to:

- plugin metadata and connection form
- connection parsing and connectivity testing
- Catalog metadata discovery

SQL execution/query contracts are not part of the current plugin boundary.

### `yak-ops-boot`

Owns final application assembly, all HTTP Controllers, ControllerAdvice, health and global runtime configuration. `GlobalExceptionHandler` is the single fallback HTTP exception outlet; capability-specific advice only keeps behavior that requires capability context such as Datasource message masking.

Boot runtime configuration follows a single-runtime contract:

- one application DataSource, created from `spring.datasource` by Spring Boot
- one default transaction manager; Business/Security do not use capability-specific transaction-manager aliases
- one MyBatis-Plus SqlSessionFactory / SqlSessionTemplate created by the Starter
- one MyBatis-Plus interceptor chain; Security tenant isolation is table-scoped inside that shared chain
- MVC authentication interceptor registration
- one application OpenAPI document

Boot must not manually recreate DataSource, SqlSessionFactory, SqlSessionTemplate, TransactionManager or ObjectMapper when Spring Boot already provides the required runtime behavior.

Flyway schema history and migration SQL remain owned by `yak-ops-dao`; Boot supplies the runtime DataSource used by that persistence layer.

Hard boundary:

- every Yak Ops `@Controller` / `@RestController` lives in `yak-ops-boot`
- every Yak Ops Controller package lives under `io.yak.ops.boot.controller`
- Controller depends on Business interfaces rather than BusinessImpl / DAO / Plugin internals
- application-wide Spring infrastructure configuration lives in `yak-ops-boot`
- capability modules must not depend on Boot

### `yak-ops-ui`

Owns the browser product. Only Datasource is currently visible in product navigation.

### `yak-ops-dist`

Owns release packaging.

### `yak-ops-bom`

Owns Yak Ops dependency version alignment.

## External Framework Boundary

Yak Ops no longer depends on `yak-framework`.

The former Yak Common and Yak Security code required by the product is now owned inside this repository.

## Dependency Direction

```text
UI
 ↓ HTTP
Boot
 ├────────→ Security ─────────────→ Common
 │              └───────────────→ DAO ─→ Common
 └────────→ Datasource Business ─→ Common
                │
                ├───────────────→ DAO ─→ Common
                └───────────────→ Datasource Plugin API
                                      ↑
                                Plugin Implementations
```

Boot owns protocol entry and application assembly.

Security and Datasource own capability behavior. DAO owns persistence and schema. None of them depend on Boot.

## Refactor Rule

```text
Capability Contract
→ current ownership
→ nearest RULES
→ current code
→ minimal migration
→ explicit verification
```

Do not split classes, add modules, or introduce roles only because a file is long.
