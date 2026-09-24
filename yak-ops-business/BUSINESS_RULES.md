# Business Rules

Scope:
- `yak-ops-business/**`

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- nearest capability `*_RULES.md`
- DAO 变化加载 `/yak-ops-dao/DAO_RULES.md`
- HTTP Contract 变化加载 `/CONTROLLER_RULES.md`

## Role

`yak-ops-business` is the Yak Ops Service Layer.

It owns business facts, business rules, state transitions, transactions and persistence-facing orchestration. The module is named Business, but its responsibility is equivalent to `yakable-service`.

Boundary:

```text
Controller / Boot
       ↓
    Business
       ↓
      DAO

Business ─────→ stable Plugin / SPI capability
```

Boot owns HTTP. DAO owns persistence mechanics. Plugin implementations own provider-specific behavior. Business connects them through product semantics.

## Must

- 每个稳定业务领域或主能力边界维护一个 Business 接口。
- 使用 `XxxBusiness + impl/XxxBusinessImpl`。
- Spring 实现注解只放在 Impl；Controller 和其他 Business 只依赖接口。
- BusinessImpl 只直接访问本领域 Repository；跨领域协作通过其他 Business 接口。
- Spring 依赖统一使用 `@Resource` 和接口类型。
- Business 只暴露真实业务能力，不为了形式统一补 CRUD。
- 方法名表达业务语义；不要用 `manager / handle / process` 代替具体动作。
- Controller 请求 DTO 可以直接进入 Business；参数解析、业务校验和业务默认值归 Business。
- 对 Controller 返回公共 VO / `PagingData<VO>`，不返回 Entity、DAO Model、Repository Query 或 Plugin 实现对象。
- DAO Entity 只允许存在于 BusinessImpl 与 DAO 的内部协作链路。
- 简单 Entity → VO 转换优先留在对应 BusinessImpl 的私有方法；没有真实复用边界时不要新增 Converter / Assembler。
- 事务边界放在 BusinessImpl 的业务方法。
- 使用 Jakarta Validation；Controller 用 `@Valid`，需要方法级校验时 Impl 可用 `@Validated`。
- 每个领域保持一套异常体系，具体业务原因使用稳定错误码。
- 已校验用户输入默认保持原值；只有产品 Contract 明确要求时才做 trim / normalize。
- 稳定 Plugin / SPI 可以作为 Business 的下游能力；Business 不依赖具体插件实现。
- 内部协作者只有在拥有独立状态、缓存、协议隔离或稳定机制时才允许存在，并保持 capability-local。

## Must Not

- 引入 `BaseBusiness / BaseBusinessImpl`。
- 注入其他 `XxxBusinessImpl`。
- Controller 直接依赖 Repository、Mapper、Entity、Plugin Registry 或其他内部组件。
- Business 直接访问其他领域 Repository / Mapper。
- Business 对外返回 Entity、DAO Model 或 SPI 内部对象。
- 为了架构对称拆出没有真实 ownership 的 Manager / Reader / Coordinator / Handler / Adapter / Assembler。
- 用 Manager / Reader / Registry 作为 Controller 面向的业务入口。
- 为纯转发、异常包装、一次性转换拆独立 Spring Component。
- 为了减少单个 BusinessImpl 行数机械拆类。
- 重建一套和 DAO 重复的 Domain / Repository 层。
- 把 HTTP Request/Response 语义放进 DAO。
- 访问具体 Plugin implementation。
- 创建应用级 DataSource / MyBatis / Web 配置；应用装配属于 Boot。

## Package

默认结构：

```text
<capability>/
  XxxBusiness.java
  impl/
    XxxBusinessImpl.java
```

子能力存在独立业务 Contract 时可以继续分包：

```text
datasource/
  DataSourceBusiness.java
  impl/
    DataSourceBusinessImpl.java
  catalog/
    DataSourceCatalogBusiness.java
    impl/
      DataSourceCatalogBusinessImpl.java
  plugin/
    DataSourcePluginBusiness.java
    impl/
      DataSourcePluginBusinessImpl.java
```

`config / exception` 等包只有在 capability 确实拥有对应职责时存在。

内部缓存、诊断、匹配器等机制不需要强行改成 Business；但它们不能成为 Controller 入口，也不能形成新的业务层。

## Persistence

```text
Controller
→ Business
→ Repository
→ Mapper / Entity
→ MyBatis / SQL
```

- BusinessImpl 可以直接使用本领域 DAO Repository。
- Repository Query、Entity、DAO 聚合 Model 必须在 BusinessImpl 内转换后再跨 Business 边界。
- Schema evolution 统一由 `yak-ops-dao` 管理。

## Verification

重构 Business Layer 时至少检查：

```text
Boot only depends on Business interfaces
→ Business interfaces expose DTO / VO contracts
→ Impl owns transaction and repository orchestration
→ Entity / DAO model does not escape to Boot
→ no pass-through Manager / Reader / Adapter layer
→ Spotless check
→ relevant Maven compile/package
```
