# Controller Rules

Scope:
- Any Yak Ops Java `controller` package
- Datasource controllers currently live inside `yak-ops-business-datasource`
- App-level controllers may live in `yak-ops-boot`

Depends On:
- `JAVA_RULES.md`
- `BACKEND_TEST_RULES.md`

Owns:
- HTTP boundary
- request validation
- response contract
- permission metadata
- OpenAPI description

## Must

- Controller 只做协议转换、校验、调用 owner、返回结果。
- 业务规则留在 Datasource 业务 owner。
- 请求对象优先使用稳定 DTO，不用一组零散基础类型表达复杂业务输入。
- 对外数据使用稳定 VO，不返回 PO / Mapper object。
- 使用 Jakarta Validation 表达必填、长度和范围。
- 成功/失败复用现有统一 Result / exception handling。
- 使用 Swagger 3 / OpenAPI 3 描述公开 API。
- 权限规则必须在 Controller contract 中清晰可见。
- 简单方法保持直接，不增加无意义 facade。

## Must Not

- 在 Controller 写数据库查询或持久化。
- 直接调用 Mapper。
- 为统一异常包装写重复 try/catch。
- 返回 Map 代替已经稳定的业务响应模型。
- 暴露 SQL、表名、Mapper、堆栈或数据库实现细节。
- 为 Controller 再创建只做一层转发的 Handler / Adapter。

## Tests

HTTP Contract 变化必须按 `BACKEND_TEST_RULES.md` 提供回归证据。
