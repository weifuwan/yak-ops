# Security Rules

Scope:
- yak-ops-security/**

Depends On:
- /ARCHITECTURE.md
- /JAVA_RULES.md
- Controller changes also load /CONTROLLER_RULES.md

Owns:
- user management
- login / logout / current user
- authentication session runtime
- security database schema and migration

## Current Product Boundary

Yak Ops 当前只发布两组 Security API：

- /yak-security/api/v1/account/**
- /yak-security/api/v1/user/**

Role、Permission、Department、Project、Message、Oplog、Resource 等历史迁移代码不再作为当前对外 Security API。

Security Java namespace 统一为 `io.yak.ops.security`，由 Yak Ops 主应用直接扫描。

## Authentication

登录态统一使用 Servlet HttpSession。

浏览器通过 JSESSIONID Cookie 携带登录态，不再依赖第三方 Token 框架，也不再维护独立 Redis Token 存储。

## Must

- keep passwords encoded and never return stored password hashes.
- keep login errors stable and avoid leaking sensitive credential detail.
- keep authentication implementation behind AuthenticationManager.
- keep security database migration SQL owned by this module.
- keep Spring application wiring in `yak-ops-boot`; Security classes use normal component scanning.
- reuse io.yak.framework.common contracts from yak-ops-common.

## Must Not

- depend on external yak-framework modules.
- add `spring.factories`, Boot AutoConfiguration metadata, or module-level application assembly.
- create a second Security connection pool; Security reuses the shared `yak.database` datasource.
- add another Token / RBAC framework as a replacement.
- expose new Role / Permission / Department / Project / Message / Oplog / Resource Security APIs.
- bypass UserService with ad hoc user SQL from Boot or Datasource.
- recreate removed tests or CI as a side effect.
