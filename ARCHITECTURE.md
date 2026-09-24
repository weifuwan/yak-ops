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

## Current Modules

### `yak-ops-common`

Owns shared contracts and the unified `io.yak.framework.common` Result / ErrorCode / BusinessException / PageData contracts migrated into Yak Ops.

### `yak-ops-security`

Owns user management, login/logout/current identity, HttpSession authentication state, authentication interceptor/runtime, and the security schema.

Security does not own Controller or ControllerAdvice. Boot exposes the current Security HTTP API by calling Security-owned services.

Security production code was migrated from `yak-framework/yak-security`.

The Java package `io.yak.framework.security` is temporarily preserved as a naming residue. It no longer means an external framework dependency.

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

Owns the Datasource domain: business rules, connection, catalog, SQL execution, current concrete persistence and Datasource-specific security policy.

Datasource does not own Controller, ControllerAdvice or Controller-only request/response conversion. Boot exposes Datasource HTTP APIs by calling Datasource-owned capability.

### `yak-ops-plugins/yak-ops-plugin-datasource`

Owns Datasource provider contracts and implementations.

### `yak-ops-boot`

Owns final application assembly, all HTTP Controllers, ControllerAdvice, Controller-only request/response conversion, health and global configuration.

Hard boundary:

- every Yak Ops `@Controller` / `@RestController` lives in `yak-ops-boot`
- every Yak Ops Controller package lives under `io.yak.ops.boot.controller`
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
 └────────→ Datasource Business ─→ Common
                │
                ├───────────────→ Security identity/runtime
                └───────────────→ Datasource Plugin API
                                      ↑
                                Plugin Implementations
```

Boot owns protocol entry and application assembly.

Security and Datasource own capability behavior. They never depend on Boot.

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
