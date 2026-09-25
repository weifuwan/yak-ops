# Common Rules

Scope:
- `yak-ops-common/**`

Depends On:
- `/JAVA_RULES.md`

Owns:
- Datasource shared DTO / VO
- cross-domain stable constants
- Datasource enums
- Security user/login shared HTTP DTO / VO contracts
- unified Result / ErrorCode / PageData contracts
- cross-domain business exception base under `io.yak.ops.common.exception`
- small shared infrastructure with no better owner

## Namespace

All shared Common contracts use the `io.yak.ops.common` namespace.

Package ownership:

```text
io.yak.ops.common.result     → Result / ErrorCode
io.yak.ops.common.page       → PageData / PagingData
io.yak.ops.common.exception  → shared business exceptions
io.yak.ops.common.bean       → shared DTO / VO
io.yak.ops.common.enums      → shared enums, including enums/common/CommonErrorCode
io.yak.ops.common.constant   → CommonConstants for cross-domain stable constants
io.yak.ops.common.util       → reusable stateless utilities such as DateUtils / IdUtils / StringUtils / ObjectUtils / CollectionUtils / BeanCopyUtils / JSONUtils
```

Do not place production Java types directly under `io.yak.ops.common`. New shared types must have an explicit owner package.
Do not recreate root-level compatibility wrappers after a type has moved to its owned package.

## Specialized Rules

DTO / VO 变更必须同时加载 [DTO_VO_RULES.md](./DTO_VO_RULES.md)。该文档统一定义请求 / 响应边界、时间格式、分页、排序和 DTO / VO 命名规则。

## Constant Ownership

- `CommonConstants` 是全仓唯一的跨领域公共常量入口，只承载稳定、不可配置、跨领域共享的代码契约。
- 领域常量必须留在对应领域 owner 模块；Common 不创建 `constant.<domain>` 作为领域常量中转层。
- 领域 API Prefix 必须基于公共 `CommonConstants.API_PREFIX` 组合，禁止重复硬编码全局 API 根路径。
- 分页默认页码、默认 page size、最大 page size、最大排序字段数等全局分页约定统一由 `CommonConstants` 提供。
- 不允许重新创建 `SystemConstant` / `SystemConstants` 等与 `CommonConstants` 重叠的全局常量容器。
- Common 不是常量垃圾桶；类型和状态优先使用 enum，可配置值必须进入配置体系。

## Utility Boundary

- 字符串空白和 trim-to-null 统一由 `StringUtils` 提供。
- 对象 null 语义统一由 `ObjectUtils` 提供；集合 empty 语义统一由 `CollectionUtils` 提供。
- 简单同名属性复制统一由 `BeanCopyUtils` 提供，不在 Security / Datasource 重复创建 Bean copy utility。
- JSON 解析、JSON tree 创建和 JSON 序列化统一通过 `JSONUtils`；领域代码不得自行维护通用 parser / writer。
- Common Utils 必须保持无领域编排、无 HTTP 语义、无 DAO 依赖；领域异常转换留在领域 owner。
- 业务代码可以操作 `JsonNode` 表达领域逻辑，但 JSON parser / writer ownership 必须收口到 Common。

## Enum Contracts

Common 中带字段的枚举统一保持不可变契约：

- 使用 `@Getter` + `@RequiredArgsConstructor`，不手写纯样板 constructor / getter。
- 枚举字段使用 `private final`。
- ErrorCode 枚举统一使用 `code` / `message`。
- 普通业务枚举的展示文案统一使用 `displayName`；业务标识字段使用明确名称，例如 `code`。
- 持久化数值枚举统一使用 `value` 字段并标记 `@EnumValue`，Business / Service 不手动进行 0/1/2 转换。
- 枚举常量本身表达 Java 语义，数据库存储值属于 Persistence Contract。
- 只有数据库列、Flyway、Entity 和查询逻辑在同一个变更中完成迁移时，才允许给持久化字段增加 `@EnumValue` 或等价映射。

禁止为了减少几行代码提前改变枚举的数据库存储语义。

## Must

- unified Result / ErrorCode / BusinessException / PageData Contract only has one implementation.
- `BusinessException` stays independent from HTTP/Spring Web and acts as the cross-domain exception base.
- public shared objects remain behavior-free.
- Security HTTP DTO / VO may stay in Common when Boot and Security share them, but Security-specific error codes, exceptions and internal models must stay in `yak-ops-security`.
- existing MyBatis shared configuration is reused where appropriate.

## Must Not

- depend on external yak-framework modules.
- put Datasource or Security business orchestration in Common.
- put Datasource / Security domain constants in Common when they are not truly cross-domain contracts.
- put Security-specific error codes, exceptions or internal domain models in Common.
- put HTTP status or ControllerAdvice behavior into Common.
- use Common as a miscellaneous dumping ground.
- add root-level classes directly under `io.yak.ops.common`.
- recreate deleted tests or CI.

## Boundary

Common stores stable cross-module DTO / VO contracts, cross-domain bases and small infrastructure only. Domain-specific exceptions and error codes belong to their capability owner; HTTP exception mapping belongs to `yak-ops-boot`.
