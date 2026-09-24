# Security Rules

Scope:
- `yak-ops-security/**`

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- Controller changes also load `/CONTROLLER_RULES.md`

Owns:
- user management
- login / logout / current identity
- authentication session runtime
- permissions required by current Datasource APIs
- role and project membership required by current identity and Project Space
- security database schema and migration

## Current Fact

Security code was migrated from `yak-framework/yak-security` into this repository.

The Java namespace `io.yak.framework.security` is temporarily preserved to avoid mixing dependency removal with a broad namespace rewrite.

This module is now the source owner. Do not add `io.github.weifuwan:yak-security-spring-boot-starter` back.

## Product Focus

The primary supported product capabilities are:

```text
User Management
Login / Logout
Current User
Datasource Permission Check
Project Membership / Project Access
```

Other migrated Security code exists for behavior compatibility but is not a reason to expand Yak Ops product scope. When touched, prefer removal or simplification unless current behavior still requires it.

## Must

- keep authentication implementation behind the existing AuthenticationManager / CurrentUser boundaries.
- keep passwords encoded and never return stored password hashes.
- keep login errors stable and avoid leaking sensitive credential detail.
- keep current-user permission/project resolution fail-closed.
- keep permission annotations declarative at Controller boundaries.
- keep database migration ownership inside this module.
- reuse the internal `io.yak.framework.common` contracts from `yak-ops-common`.

## Must Not

- depend on external yak-framework modules.
- create a second authentication stack beside Sa-Token.
- bypass UserService / ProjectService with ad hoc SQL from Boot or Datasource.
- add new Message / Oplog / Resource security features unless a current Datasource requirement needs them.
- recreate removed tests or CI as a side effect.

## Boundary

Security owns identity and authorization facts.

Datasource owns Datasource business policy.

Boot owns final assembly and Project Space adapter code.
