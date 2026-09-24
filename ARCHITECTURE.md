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

Owns shared Datasource-facing DTO / VO / PO / enum / constant and small reusable infrastructure.

Legacy non-Datasource residue that still exists in Common is not an approved extension point. Do not add new code to those areas.

### `yak-ops-spi`

Reserved extension boundary.

Current Datasource plugin contracts do not live here; they live in `yak-ops-plugin-datasource-api`.

Do not add a new SPI here unless a concrete cross-module extension contract requires it.

### `yak-ops-core`

Reserved empty module.

It currently has no production code.

Do not move code into Core merely because it looks reusable or because another class is large. A future Core capability must first prove an independent lifecycle or stable runtime contract.

### `yak-ops-business/yak-ops-business-datasource`

Owns the Datasource domain end to end:

- HTTP controllers for Datasource capabilities
- business rules and commands
- queries
- connection behavior
- catalog behavior
- SQL execution behavior
- persistence
- repository / DAO / mapper
- Datasource Flyway migrations
- Datasource-specific security and exceptions

The module currently also contains the SQL Execution and Project Context contracts migrated out of the old Core module. Their Java package still uses `io.yak.ops.core.*`; this is a naming residue, not a separate module boundary.

### `yak-ops-plugins/yak-ops-plugin-datasource`

Owns Datasource extension contracts and concrete datasource implementations.

Submodules:

```text
yak-ops-plugin-datasource-api
yak-ops-plugin-datasource-jdbc
yak-ops-plugin-datasource-doris
yak-ops-plugin-datasource-elasticsearch
yak-ops-plugin-datasource-mongodb
yak-ops-plugin-datasource-all
```

`api` owns stable plugin contracts. Concrete providers depend on the API. Business code must not depend on a concrete provider implementation.

### `yak-ops-boot`

Owns final application assembly and global application boundaries:

- Spring Boot entry
- application configuration
- health
- global compatibility/controller endpoints
- Project Space runtime and security integration

Datasource product controllers currently live in the Datasource business module. Do not move them to Boot only for symmetry.

### `yak-ops-ui`

Owns the browser product.

Only Datasource is currently visible in product navigation.

### `yak-ops-dist`

Owns release packaging.

### `yak-ops-bom`

Owns dependency version alignment.

## Dependency Direction

Current active dependency direction is approximately:

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

Boot ─→ Datasource Business
Boot ─→ Datasource Plugin All

Core = reserved empty module
SPI  = reserved minimal module
```

Rules:
- Datasource Business must not depend on concrete datasource provider implementations.
- Provider implementations depend on the Datasource Plugin API, not on Datasource Business internals.
- Common must not own Datasource business orchestration.
- Boot assembles the application; it must not become a second Datasource business layer.
- Core and SPI are not default destinations for new code.

## Datasource Internal Ownership

Use package ownership before creating new layers:

```text
controller   HTTP contract
management   mutation / lifecycle commands
query        read behavior
domain       Datasource business facts
connection   connection normalization / testing
catalog      catalog read behavior and policy
execution    SQL execution runtime and audit behavior
gateway      business ports to plugin capabilities
repository   persistence-facing business boundary
dao          MyBatis persistence implementation
config       module configuration
security     Datasource-specific security
exception    Datasource error mapping
```

Package names describe current owners. Do not add a new package role because another architecture uses one.

## Transport Boundary

Datasource HTTP controllers currently live in the Datasource module.

Controllers own protocol conversion only. Domain decisions belong to the Datasource owner behind them.

Boot owns application-wide transport concerns, not Datasource business policy.

## Persistence Boundary

Datasource persistence stays inside the Datasource module:

```text
Repository
→ DAO / Mapper
→ MyBatis / SQL
→ Flyway-owned schema
```

Simple persistence should stay simple. Do not add forwarding layers without behavior.

## Plugin Boundary

`yak-ops-plugin-datasource-api` is the stable provider contract.

Must:
- keep provider-specific driver/config behavior in provider modules
- keep business policy in Datasource Business
- keep contracts provider-neutral

Must Not:
- import concrete provider implementations from Business
- let one provider's protocol define the general Datasource contract
- add a second plugin abstraction when the current API already expresses the capability

## Refactor Rule

Architecture changes migrate current facts; they do not rebuild the product from scratch.

Before moving code:

```text
Capability Contract
→ current ownership
→ nearest RULES
→ current code + tests
→ minimal migration
→ verification
```

Do not split classes, add modules, or introduce roles only because a file is long.
