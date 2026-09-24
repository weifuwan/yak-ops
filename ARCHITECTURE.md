# Yak Ops Architecture

Status: Active

Scope:
- Current Yak Ops Datasource-only product architecture
- Module ownership and dependency direction

Depends On:
- `docs/engineering-context-model.md`

## Principle

Yak Ops is currently a Datasource product.

Architecture follows current ownership, not historical modules and not a future platform plan.

A module or layer exists only when it owns a real boundary.

## Current Modules

### `yak-ops-common`

Owns shared Datasource DTO / VO / PO / enum / constant and small reusable infrastructure.

It also owns the unified `io.yak.framework.common` Result / ErrorCode / BusinessException / PageData contracts migrated from Yak Framework.

### `yak-ops-dao`

Owns shared database persistence infrastructure.

Current scope is intentionally small:

- MyBatis-Plus Repository base contract
- MyBatis-Plus Repository base implementation
- persistence rules shared by concrete DAO code

The module keeps the ID type generic because current Yak Ops tables still use different persistence conventions. Concrete Datasource persistence remains in `yak-ops-business-datasource` until a separate migration changes that ownership.

### `yak-ops-spi`

Reserved minimal extension boundary.

Datasource plugin contracts live in `yak-ops-plugin-datasource-api`.

### `yak-ops-core`

Reserved empty module.

Do not move code here merely because it looks reusable or because another class is long.

### `yak-ops-business/yak-ops-business-datasource`

Owns the Datasource domain end to end:

- HTTP controllers
- business rules and commands
- queries
- connection behavior
- catalog behavior
- SQL execution behavior
- current concrete persistence
- current repository / DAO / mapper
- current Datasource Flyway migrations
- Datasource security and exceptions

SQL Execution and Project Context physically live here even though some Java packages still use `io.yak.ops.core.*`.

### `yak-ops-plugins/yak-ops-plugin-datasource`

Owns Datasource extension contracts and concrete datasource implementations.

`api` owns stable provider contracts. Concrete providers depend on the API. Business code must not depend on concrete provider implementations.

### `yak-ops-boot`

Owns final application assembly and global application boundaries:

- Spring Boot entry
- application configuration
- health
- global compatibility/controller endpoints
- Project Space runtime and security integration

### `yak-ops-ui`

Owns the browser product.

Only Datasource is currently visible in product navigation.

### `yak-ops-dist`

Owns release packaging.

### `yak-ops-bom`

Owns dependency version alignment directly.

Yak Ops no longer imports `yak-framework-parent`.

## External Framework Boundary

The only remaining active Yak Framework runtime dependency is Yak Security.

`yak-common` is internalized into `yak-ops-common`.

## Dependency Direction

```text
UI
 ↓ HTTP
Datasource Business ─────→ Common
       │
       └───────────────→ Datasource Plugin API
                              ↑
Datasource Plugin Implementations
       ↑
Datasource Plugin All

DAO ─→ Common

Boot ─→ Datasource Business
Boot ─→ Datasource Plugin All
Boot ─→ Yak Security

Core = reserved empty module
SPI  = reserved minimal module
```

Datasource Business does not depend on DAO yet; concrete persistence migration is a separate refactor.

## Datasource Internal Ownership

```text
controller   HTTP contract
management   mutation / lifecycle commands
query        read behavior
domain       Datasource business facts
connection   connection normalization / testing
catalog      catalog read behavior and policy
execution    SQL execution runtime and audit behavior
gateway      business ports to plugin capabilities
repository   current persistence-facing business boundary
dao          current MyBatis persistence implementation
config       module configuration
security     Datasource-specific security
exception    Datasource error mapping
```

## Refactor Rule

Architecture changes migrate current facts; they do not rebuild the product from scratch.

Before moving code:

```text
Capability Contract
→ current ownership
→ nearest RULES
→ current code
→ minimal migration
→ explicit verification
```

Do not split classes, add modules, or introduce roles only because a file is long.
