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

## Current Modules

### `yak-ops-common`

Owns shared contracts and the unified `io.yak.framework.common` Result / ErrorCode / BusinessException / PageData contracts migrated into Yak Ops.

### `yak-ops-security`

Owns user management, login/logout/current identity, HttpSession authentication state, and the security schema.

Security production code was migrated from `yak-framework/yak-security`.

Security code uses the native `io.yak.ops.security` namespace and participates in normal application component scanning.

### `yak-ops-dao`

Owns shared database persistence infrastructure:

- MyBatis-Plus Repository base contract
- MyBatis-Plus Repository base implementation
- persistence rules shared by concrete DAO code

Concrete Datasource persistence remains in `yak-ops-business-datasource` until a separate migration changes that ownership.

### `yak-ops-spi`

Reserved minimal extension boundary.

### `yak-ops-core`

Reserved empty module.

### `yak-ops-business/yak-ops-business-datasource`

Owns the Datasource domain end to end: HTTP, business rules, connection, catalog, SQL execution, current concrete persistence and Datasource-specific security policy.

### `yak-ops-plugins/yak-ops-plugin-datasource`

Owns Datasource provider contracts and implementations.

### `yak-ops-boot`

Owns the application composition root, Spring wiring, shared runtime infrastructure, health and global configuration.

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
Security ─────────────→ Common
   ↑
Boot ─→ Datasource Business ─→ Common
 │          │
 │          └──────────────→ Datasource Plugin API
 └────────→ DAO                    ↑
                           Plugin Implementations
```

Boot assembles Security, Datasource Business and Datasource Plugin All. Security reuses the shared `yak.database` connection pool; only Security-specific MyBatis and migration behavior remains explicitly wired in Boot.

Datasource may depend on Security identity contracts but must not own user/login persistence.

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
