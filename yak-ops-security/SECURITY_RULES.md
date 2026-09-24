# Security Rules

Scope:
- yak-ops-security/**

Depends On:
- /ARCHITECTURE.md
- /JAVA_RULES.md
- Schema changes also load /yak-ops-dao/FLYWAY_RULES.md
- HTTP contract changes also load /CONTROLLER_RULES.md

Owns:
- user management
- login / logout / current user
- authentication session runtime

## Current Product Boundary

Yak Ops 当前只发布两组 Security API：

- /yak-security/api/v1/account/**
- /yak-security/api/v1/user/**

Role、Permission、Department、Project、Message、Oplog、Resource、Notification 等旧体系不再属于当前 Security runtime。

Java namespace io.yak.framework.security 暂时保留，避免把依赖清理和包名迁移混在同一次改造中。

## HTTP Boundary

Security HTTP Controller 和 ControllerAdvice 统一由 `yak-ops-boot` 持有。

本模块提供登录、用户管理和认证运行时能力，不创建 `controller` package，也不依赖 Boot。

现有 API 路径保持不变，由 Boot 暴露。

## Model Boundary

Security 只保留用户和登录所需模型：

- DTO: `PageParamDTO`、`account/AccountLoginDTO`、`user/UserDTO`、`user/UserQueryDTO`、`user/UserPasswordResetDTO`
- VO: `user/UserVO`、`user/UserBriefVO`、`user/CurrentUserVO`
- Enum: `ResultCode`、`user/UserCheckType`
- PO: `AppBasePO`、`BasePO`、`UserPO`

用户模型不得重新携带 role / permission / project / menu 等旧授权字段。

Notification capability 已删除，不在 Security 中保留 publisher、message DTO/VO 或自动装配。

## Authentication


登录态统一使用 Servlet HttpSession。

浏览器通过 JSESSIONID Cookie 携带登录态，不再依赖第三方 Token 框架，也不再维护独立 Redis Token 存储。

## Must

- keep passwords encoded and never return stored password hashes.
- keep login errors stable and avoid leaking sensitive credential detail.
- keep authentication implementation behind AuthenticationManager.
- route every Security schema change through `/yak-ops-dao/FLYWAY_RULES.md`.
- reuse io.yak.framework.common contracts from yak-ops-common.

## Must Not

- depend on external yak-framework modules.
- add another Token / RBAC framework as a replacement.
- expose new Role / Permission / Department / Project / Message / Oplog / Resource Security APIs.
- bypass UserService with ad hoc user SQL from Boot or Datasource.
- recreate removed tests or CI as a side effect.
- add Controller / RestController / RestControllerAdvice to this module.
- add Flyway beans or versioned SQL migrations to this module.
- reintroduce Role / Permission / Project / Resource / Message / Oplog / Notification runtime.
- add non-user/login DTO / VO / Enum / PO models to this module.
