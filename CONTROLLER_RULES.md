# Controller Rules

Scope:
- `yak-ops-boot/src/main/java/io/yak/ops/boot/controller/**`
- Any change that introduces or moves a Yak Ops `@Controller`, `@RestController` or `@RestControllerAdvice`

Depends On:
- `ARCHITECTURE.md`
- `JAVA_RULES.md`
- nearest capability RULES for the API being exposed

Owns:
- HTTP boundary
- request validation
- response contract
- OpenAPI description
- HTTP exception mapping
- Controller-only request/response conversion

## Placement

All Yak Ops Controllers belong to `yak-ops-boot`.

```text
yak-ops-boot
└── src/main/java/io/yak/ops/boot/controller
    ├── datasource
    ├── workspace
    ├── exception
    │   └── GlobalExceptionHandler.java
    └── security
```

Must:
- `@Controller` / `@RestController` 只允许出现在 `yak-ops-boot`。
- `@RestControllerAdvice` 和只服务于 Controller 的 converter 也放在 `yak-ops-boot/controller` 边界内。
- 业务异常、参数异常、HTTP 状态异常和未知异常统一由 `controller/exception/GlobalExceptionHandler` 处理。
- 只有无法通过 `BusinessException` / `ErrorCode` 表达的真实 capability HTTP 语义，才允许新增 capability-specific ControllerAdvice。
- Controller 通过 Boot → capability owner 的单向依赖调用 Security / Workspace / Datasource。
- Security / Datasource / DAO / Core / SPI / Plugin 不得依赖 Boot。
- 新增 API 时先确定 capability owner，再由 Boot 暴露 HTTP contract。

## Endpoint Lifecycle

HTTP Endpoint 是产品 Contract，不是 Business 方法的自动镜像。

Must:
- 新增 Endpoint 必须能对应当前 Product Capability、明确外部消费者或已批准的稳定集成 Contract。
- 删除产品能力时，同一变更中清理专属 Controller、DTO / VO、Business 转换和无人调用的持久化路径。
- 后端能力允许暂时没有 Web UI，但必须在 capability RULES 中明确属于当前产品边界。

Must Not:
- 因为 Business / Repository 已有方法就顺手暴露 HTTP API。
- 保留只用于历史联调、框架验证、临时 ping 的 Controller。
- 仅以“以后可能会有页面或调用方”为理由发布新 Endpoint。
- 在业务模块或 Security 模块创建 `controller` package。
- 为了复用 Controller 逻辑让 capability module 反向依赖 Boot。
- 把业务规则搬到 Boot，只因为 Controller 已经位于 Boot。

## Controller Behavior

Must:
- Controller 只做协议转换、校验、调用 owner、返回结果。
- 业务规则留在对应 capability owner。
- 请求对象优先使用稳定 DTO，不用一组零散基础类型表达复杂业务输入。
- 对外数据使用稳定 VO，不返回 Entity / Mapper object。
- 使用 Jakarta Validation 表达必填、长度和范围。
- 成功/失败复用现有统一 Result / exception handling。
- 使用 Swagger 3 / OpenAPI 3 描述公开 API。
- 只有存在真实运行时授权能力时，Controller 才声明权限 metadata；禁止保留无人读取的 permission annotation。
- 简单方法保持直接，不增加无意义 facade。

Must Not:
- 在 Controller 写数据库查询或持久化。
- 直接调用 Mapper。
- 为统一异常包装写重复 try/catch。
- 返回 Map 代替已经稳定的业务响应模型。
- 暴露 SQL、表名、Mapper、堆栈或数据库实现细节。
- 为 Controller 再创建只做一层转发的 Handler / Adapter。
- 添加没有运行时消费者的权限注解或权限常量。
- 在 capability-specific ControllerAdvice 中重复处理通用参数异常、`BusinessException` 或兜底 `Exception`。
