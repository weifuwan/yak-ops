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
- stable Security constants shared by Security and Boot
- Security domain error codes and exceptions
- Security internal domain models

## Current Product Boundary

Yak Ops 当前只发布两组 Security API：

- /yak-security/api/v1/account/**
- /yak-security/api/v1/user/**

Role、Permission、Department、Project、Message、Oplog、Resource、Notification 等旧体系不再属于当前 Security runtime。

Security runtime 统一使用 `io.yak.ops.security` namespace。HTTP DTO / VO 等跨模块接口对象由 `yak-ops-common` 持有；Security 专属错误码、异常和内部领域模型由 `yak-ops-security` 持有；持久化 Entity / Mapper / Repository 由 `yak-ops-dao` 持有。Security 不再保留 legacy `common` package 或 Spring Boot `autoconfigure` package。

## Constant Boundary

`SecurityConstants` 只承载 Security 与 Boot 真实共享、稳定且不可配置的领域契约：

- `CONFIG_PREFIX`
- Security API 根路径
- Account / User API Prefix
- 登录公开路径

Security API 根路径必须基于 `CommonConstants.API_PREFIX` 组合，不重复定义全局 `/api/v1`。

只在一个类使用的校验长度、Pattern、Session key、表名等继续使用该类自己的 `private static final`，不得为了“统一”全部搬进 `SecurityConstants`。

不得在 Common 创建 `SecurityConstants` 副本，也不得重新引入 Role / Permission 等无运行时消费者的权限常量。

## HTTP Boundary

Security HTTP Controller 和 ControllerAdvice 统一由 `yak-ops-boot` 持有。

本模块提供登录、用户管理和认证运行时能力，不创建 `controller` package，也不依赖 Boot。

现有 API 路径保持不变，由 Boot 暴露。

认证拦截器实现仍由 Security 持有；将拦截器注册进 Spring MVC、OpenAPI / Swagger 以及其他应用级 Web 装配统一由 Boot 持有。

## Utility Boundary

Security 不维护无领域语义的通用工具类。

统一复用 Common：
- `StringUtils`：blank / non-blank / trim-to-null
- `ObjectUtils`：null / non-null
- `CollectionUtils`：empty / non-empty
- `BeanCopyUtils`：简单 DTO / VO / Model 同名属性复制
- `JSONUtils`：JSON parse / serialize

禁止重新创建 `CopyBeanUtil`、私有 `normalize(String)`、重复 `value == null || value.trim().isEmpty()` 之类的通用实现。

Security-specific 校验、异常和领域模型仍由 Security owner 持有，不得下沉进 Common Utils。

## Infrastructure Boundary

Security 不创建独立连接池、SqlSessionFactory、SqlSessionTemplate 或事务管理器。

Security 只提供用户/登录行为和安全数据隔离所需的语义；最终 DataSource / MyBatis-Plus runtime wiring 由 `yak-ops-boot` 统一装配，Security user persistence 仍由 `yak-ops-dao` 持有。

## Service Boundary

Security 只暴露两条稳定 Service contract：

```text
LoginService
→ LoginServiceImpl

UserService
→ UserServiceImpl
```

Boot 只依赖这两个接口，不得直接注入 Security 的 concrete service / impl。

User 领域的新增、编辑、删除、密码重置、自删除校验、密码变更后会话清理都属于 `UserServiceImpl` 行为，不再拆 `UserAdministrationService` 或其他第二业务入口。

没有真实 HTTP / Service 调用方的 `forceLogout` 之类能力不保留未来接口；需要时由新的 Capability Contract 再引入。

## Model Boundary

Security runtime 只保留用户和登录业务行为、认证状态及内部领域模型。`LoginService` 是当前登录行为 contract，不再叠加 `LoginExtend` 或纯转发 facade。

跨模块 HTTP Contract 继续由 `yak-ops-common` 持有：

- DTO: `AccountLoginDTO`、`UserDTO`、`UserQueryDTO`、`UserPasswordResetDTO`；`UserQueryDTO` 统一继承 Common `PageQueryDTO`
- VO: `UserVO`、`UserBriefVO`、`CurrentUserVO`
- Persistence-shared Enum: `UserStatus` 暂留 Common，因为 DAO Entity 直接持有其 MyBatis `@EnumValue` 映射；后续若拆分 Persistence Enum 与 Domain Enum，再单独迁移

Security 领域契约由本模块持有：

- ErrorCode: `io.yak.ops.security.enums.SecurityErrorCode`
- Exception: `io.yak.ops.security.exception.YakSecurityException`
- Model: `UserAccount`、`UserCheckType`

Common 不得重新创建 `ResultCode`、`YakSecurityException`、`UserCheckType` 等 Security 专属类型。

用户持久化统一由 `yak-ops-dao` 持有：

- Entity: `UserEntity`
- Mapper: `UserMapper`
- Repository: `UserRepository` / `UserRepositoryImpl`

Security Service 不直接访问 Mapper，也不向上泄漏 MyBatis `IPage`。

用户分页请求统一使用 `pageNo / pageSize`。自定义 `sorts` 在 Security Repository 排序白名单落地前必须明确拒绝，禁止静默忽略；默认分页排序固定为 `createTime DESC, id DESC` 保证稳定翻页。

用户模型不得重新携带 role / permission / project / menu 等旧授权字段。

Notification capability 已删除，不在 Security 中保留 publisher、message DTO/VO 或自动装配。

## Authentication

登录态统一使用 Servlet HttpSession。

浏览器通过 JSESSIONID Cookie 携带登录态，不再依赖第三方 Token 框架，也不再维护独立 Redis Token 存储。

## Must

- keep passwords encoded and never return stored password hashes.
- keep login errors stable and avoid leaking sensitive credential detail.
- keep authentication implementation behind AuthenticationManager.
- keep login orchestration in `LoginService`; do not add parallel extension/facade layers without a real second implementation.
- keep user management behind the single `UserService` contract; password reset, deletion validation and session invalidation stay inside `UserServiceImpl`.
- Boot must not inject `UserServiceImpl` or any secondary user administration service.
- route every Security schema change through `/yak-ops-dao/FLYWAY_RULES.md`.
- reuse shared HTTP / utility contracts from yak-ops-common.
- throw `io.yak.ops.security.exception.YakSecurityException` for Security business failures.
- use `SecurityErrorCode` for Security-specific failures and `CommonErrorCode` for true cross-domain failures such as generic invalid parameters.
- access user persistence only through `UserRepository` from yak-ops-dao.

## Must Not

- depend on external yak-framework modules.
- add another Token / RBAC framework as a replacement.
- expose new Role / Permission / Department / Project / Message / Oplog / Resource Security APIs.
- bypass UserService with ad hoc user SQL from Boot or Datasource.
- access `UserMapper` directly from Security Service.
- recreate removed tests or CI as a side effect.
- add Controller / RestController / RestControllerAdvice to this module.
- add application DataSource / MyBatis-Plus / OpenAPI / MVC registration configuration to this module.
- add Spring Boot auto-configuration registration or `META-INF/spring.factories` to this module.
- add Flyway beans or versioned SQL migrations to this module.
- reintroduce Role / Permission / Project / Resource / Message / Oplog / Notification runtime.
- add no-op RBAC annotations or permission constants without an enforcing runtime consumer.
- add non-user/login DTO / VO / Enum / persistence Entity models to this module.
