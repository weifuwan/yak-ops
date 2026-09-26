# Yak Ops Architecture

Status: Active

Scope:
- Current Yak Ops Datasource product architecture
- Workspace business ownership boundary
- Supporting user/login/security capability
- Module ownership and dependency direction

Depends On:
- `docs/engineering-context-model.md`

## Principle

Yak Ops is currently a Datasource product with Workspace as the shared business ownership boundary.

User/Login/Security is a supporting platform capability required to access the product. Workspace is separate from Security: authentication answers who the user is, while Workspace answers which business data boundary is active.

Architecture follows current ownership, not historical modules and not a future platform plan.

HTTP is an application boundary. All Controller ownership belongs to `yak-ops-boot`; capability modules expose business capability to Boot and never own HTTP entry classes.

Application runtime infrastructure is also a Boot boundary. DataSource/MyBatis-Plus runtime policy, OpenAPI/Swagger and MVC interceptor/filter registration belong to `yak-ops-boot`. Capability modules provide behavior and persistence contracts without assembling the final Spring application. Boot prefers Spring Boot/Starter auto-configuration over manually recreating framework beans.

## Current Modules

### `yak-ops-common`

Owns shared data contracts for Datasource, Workspace and Security, plus the unified Result / ErrorCode / PageData contracts, request `WorkspaceContext` and the cross-domain `BusinessException` base. Security HTTP DTO / VO remain here when Boot and Security share them, but Security-specific error codes, exceptions and internal models do not.

### `yak-ops-security`

Owns user management, login/logout/current identity, HttpSession authentication state, authentication policy and the authentication interceptor implementation. It does not own RBAC administration, project authorization, messaging or notification runtime.

Security does not own Controller, ControllerAdvice, OpenAPI configuration, connection-pool/MyBatis assembly or MVC interceptor registration. Boot exposes and wires the current Security HTTP capability by calling Security-owned services and registering Security-owned behavior.

Security production code was migrated from `yak-framework/yak-security`.

Security business/runtime code uses the `io.yak.ops.security` product namespace. `SecurityErrorCode`, `YakSecurityException`, `UserAccount` and `UserCheckType` are Security-owned domain contracts. Shared HTTP DTO / VO live in `io.yak.ops.common`, while user persistence is owned by `yak-ops-dao`. `UserStatus` temporarily remains Common because DAO persistence directly owns its MyBatis enum mapping. Security exposes exactly two stable Service entries to Boot: `LoginService` and `UserService`; user administration behavior is consolidated inside `UserServiceImpl` rather than split into a second concrete service.

### `yak-ops-dao`

Owns shared database persistence infrastructure:

- MyBatis-Plus Repository base contract
- MyBatis-Plus Repository base implementation
- persistence rules shared by concrete DAO code
- the single Flyway configuration and schema history for all Yak Ops modules
- all versioned SQL under `yak-ops-dao/src/main/resources/db/migration/yak-ops`

Concrete Security user persistence, Workspace persistence and Datasource persistence are owned here.

BusinessImpl may use DAO-owned Entity/Repository internally. Entity and DAO Model do not cross the Business boundary into Boot.

DAO owns persistence and schema migration, not final application DataSource/MyBatis runtime assembly. That assembly belongs to Boot.

### `yak-ops-spi`

Reserved minimal extension boundary.

### `yak-ops-core`

Reserved empty module.

### `yak-ops-business`

Owns the application Service Layer. Stable capabilities expose one public Service Layer interface and keep Spring implementation, transactions, validation and DAO/Plugin orchestration in `impl`.

The default naming is `XxxBusiness + XxxBusinessImpl`; a capability may explicitly choose `XxxService + XxxServiceImpl` in its nearest rules. A capability must not keep both names for the same boundary.

Boot depends on stable Service Layer interfaces. Public contracts use shared DTO / VO types and do not expose DAO Entity, Mapper, Repository Query or concrete Plugin implementation details.

Detailed rules are defined in `yak-ops-business/BUSINESS_RULES.md`.

### `yak-ops-business/yak-ops-business-workspace`

Owns Workspace creation, Workspace discovery, membership and membership validation through the single stable `WorkspaceService` boundary.

Workspace is not a Security role model. Security owns authenticated identity; Workspace owns the User ↔ Workspace membership relationship and supplies the ownership boundary used by future Workspace-scoped resources.

The request Workspace ID is carried by `X-Workspace-Id`. Boot validates membership and binds the trusted value into Common `WorkspaceContext`. Missing Workspace context is globally allowed; a Workspace-scoped capability explicitly requires it.

### `yak-ops-business/yak-ops-business-datasource`

Owns only the current Datasource product behavior:

- datasource CRUD, paging and detail
- datasource connection testing
- internal datasource plugin discovery, connection parsing and secret handling

Datasource exposes exactly one public Service Layer entry: `DataSourceService`. Plugin discovery and secret handling are internal mechanisms behind `DataSourceServiceImpl`.

The module intentionally does not own Catalog HTTP/Business APIs, SQL execution, SQL audit, a duplicate Domain layer or a Gateway adapter layer.

Datasource may use DAO persistence and the stable Datasource Plugin API only behind `DataSourceServiceImpl`.

Datasource does not own Controller, ControllerAdvice, connection-pool assembly or MyBatis runtime configuration. Boot exposes Datasource HTTP APIs and supplies application infrastructure.

### `yak-ops-plugins/yak-ops-plugin-datasource`

Owns Datasource provider contracts and implementations.

The active plugin surface is limited to:

- plugin metadata and connection form
- connection parsing and connectivity testing
- Catalog metadata discovery

SQL execution/query contracts are not part of the current plugin boundary.

Datasource Providers are an open extension set. A Provider owns its stable string type, display name and compatibility aliases through the Plugin descriptor. Common and Service Layer code do not enumerate all supported database types; adding a Provider must not require a core enum change.

### `yak-ops-boot`

Owns final application assembly, all HTTP Controllers, ControllerAdvice, health and global runtime configuration. `GlobalExceptionHandler` is the single HTTP exception outlet: capability modules throw `BusinessException` with structured `ErrorCode`, and Boot centrally maps those errors to HTTP status and the unified `Result` contract.

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
- Controller depends on stable Service Layer interfaces rather than Impl / DAO / Plugin internals
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
 └────────→ DataSourceService ───→ Common
                │
                ├───────────────→ DAO ─→ Common
                └───────────────→ Datasource Plugin API
                                      ↑
                                Plugin Implementations
```

Boot owns protocol entry and application assembly.

Security, Workspace and Datasource own capability behavior. DAO owns persistence and schema. None of them depend on Boot.

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
