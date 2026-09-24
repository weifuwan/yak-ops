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

Application runtime infrastructure is also a Boot boundary. Connection-pool assembly, MyBatis-Plus runtime wiring, OpenAPI/Swagger and MVC interceptor/filter registration belong to `yak-ops-boot`. Capability modules provide behavior and persistence contracts without assembling the final Spring application.

## Current Modules

### `yak-ops-common`

Owns shared data contracts for Datasource and Security, plus the unified `io.yak.ops.common` Result / ErrorCode / BusinessException / PageData contracts migrated into Yak Ops. Security user/login DTO, VO and enum contracts live here instead of inside the Security runtime module.

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

Concrete Security user persistence (`UserEntity`, `UserMapper`, `UserRepository`) is owned here. Concrete Datasource persistence remains in `yak-ops-business-datasource` until a separate migration changes that ownership.

DAO owns persistence and schema migration, not final application DataSource/MyBatis runtime assembly. That assembly belongs to Boot.

### `yak-ops-spi`

Reserved minimal extension boundary.

### `yak-ops-core`

Reserved empty module.

### `yak-ops-business/yak-ops-business-datasource`

Owns the Datasource domain: business rules, connection, catalog, SQL execution, current concrete persistence and Datasource-specific security policy. Schema migration is owned centrally by `yak-ops-dao`.

Datasource does not own Controller, ControllerAdvice, Controller-only request/response conversion, connection-pool assembly or MyBatis runtime configuration. Boot exposes Datasource HTTP APIs and supplies application infrastructure.

### `yak-ops-plugins/yak-ops-plugin-datasource`

Owns Datasource provider contracts and implementations.

### `yak-ops-boot`

Owns final application assembly, all HTTP Controllers, ControllerAdvice, Controller-only request/response conversion, health and global runtime configuration.

Boot runtime configuration includes:

- the shared application DataSource and transaction manager
- MyBatis-Plus SqlSessionFactory / SqlSessionTemplate and plugin registration
- MVC interceptor/filter registration
- OpenAPI / Swagger UI configuration
- Jackson and other application-wide web configuration

Flyway schema history and migration SQL remain owned by `yak-ops-dao`; Boot only supplies the runtime DataSource used by that persistence layer.

Hard boundary:

- every Yak Ops `@Controller` / `@RestController` lives in `yak-ops-boot`
- every Yak Ops Controller package lives under `io.yak.ops.boot.controller`
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
                ├───────────────→ Security identity/runtime
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
