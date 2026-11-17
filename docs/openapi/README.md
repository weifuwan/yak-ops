# OpenAPI 契约

`yak-ops-3.1.json` 是平台 REST API 的 **OpenAPI 3.1** 契约基线，用于：外部集成、前端类型生成、AI 工具（MCP）接入。

## 为什么需要转换

当前技术栈 **springdoc 1.6.9 + swagger-core 2.2.0（Spring Boot 2.x）只能输出 OpenAPI 3.0.1**；
OpenAPI 3.1 需要 springdoc 2.x（Boot 3.x）。因此在导出后做一次机械升级，而不是升级整个运行时。

## 重新生成

```bash
# 1. 启动后端（Swagger 已在 swagger.properties 中开启：springdoc.api-docs.enabled=true）
# 2. 导出运行时规范
curl -s http://127.0.0.1:12345/yak-ops/v3/api-docs -o /tmp/api-docs.json
# 3. 升级到 3.1 并落库
node scripts/openapi/to-openapi-31.cjs /tmp/api-docs.json docs/openapi/yak-ops-3.1.json
```

脚本输出会打印：操作数、转换项计数、`validation=ok`。

## 升级脚本做了什么

| 规则 | 说明 |
| --- | --- |
| `openapi` | `3.0.x` → `3.1.0`，并补 `jsonSchemaDialect`（2020-12） |
| `nullable: true` | → `type: [<type>, "null"]`（无 type 时丢弃并计入 `nullableUnhandled`） |
| `exclusiveMinimum/Maximum: true` | → 数值形式（3.1 语义） |
| schema `example` | → `examples: [x]` |
| `type: file` | → `contentMediaType: application/octet-stream` |
| **鉴权补全** | 注入 `cookieAuth`（`apiKey`/`cookie`/`sessionId`）+ 全局 `security`，并对拦截器放行路径置 `security: []` |

当前基线实测：`nullable/exclusive/example/file` 转换项均为 **0**（现有注解未使用这些特性），
鉴权补全命中 8 个公开操作。

## 已知不足（后续治理项，非阻塞）

1. **44% 的操作没有 `summary`**（228/406），建议在 Controller 方法上补 `@Operation(summary=...)`；
2. **tag 命名不统一**（如 `workflow definition related operation`、`data-sync-controller`、`users related operation` 混用），建议统一为业务域（`data-sync`、`workflow`、`users`…）；
3. **`servers` 是导出时的运行时地址**（`http://127.0.0.1:12345/yak-ops`），集成方需自行替换；
4. `securitySchemes` 只有 session cookie，平台不支持 Bearer Token（`t_yak_ops_access_token` 用于 OAuth 客户端，不是本 API 的认证方式）。

## 约束提醒

- 不要手工编辑 `yak-ops-3.1.json`（下次生成会覆盖），改动请落在注解或升级脚本上；
- 升级脚本零依赖（Node 内置模块），CI 中可直接调用。
