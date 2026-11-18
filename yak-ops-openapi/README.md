# yak-ops-openapi

平台 REST API 的 **OpenAPI 3.1 契约基线与 agent 白名单**，零 Java 源码、零第三方依赖。用于：外部集成、前端类型生成、AI 工具接入。

模块内容：

| 文件 | 说明 |
| --- | --- |
| `src/main/resources/openapi/yak-ops-3.1.json` | 全量契约基线（406 操作，生成物，**禁止手改**） |
| `src/main/resources/openapi/agent-allowlist.yaml` | **agent 白名单事实源**（人工审定，PR 评审）：哪些操作对 agent 开放、风险等级、工具名映射 |
| `scripts/to-openapi-31.cjs` | 3.0→3.1 机械升级 + 治理脚本（零依赖 Node） |
| `scripts/summary-overrides.cjs` | **summary 人工文案覆盖表**（operationId → 中文 summary/description）：不改上游 Controller 注解，治理时注入，零合并冲突 |
| `scripts/check_agent_operations.py` | 三方一致性校验：白名单 ↔ agent 工具注册表 ↔ 契约（需 PyYAML） |

## 为什么需要转换

当前技术栈 **springdoc 1.6.9 + swagger-core 2.2.0（Spring Boot 2.x）只能输出 OpenAPI 3.0.1**；
OpenAPI 3.1 需要 springdoc 2.x（Boot 3.x）。因此在导出后做一次机械升级，而不是升级整个运行时。

## 重新生成

```bash
# 1. 启动后端（Swagger 已在 swagger.properties 中开启：springdoc.api-docs.enabled=true）
# 2. 导出运行时规范
curl -s http://127.0.0.1:12345/yak-ops/v3/api-docs -o api-docs.json
# 3. 升级到 3.1 并落库（在本模块目录执行）
node scripts/to-openapi-31.cjs api-docs.json src/main/resources/openapi/yak-ops-3.1.json
# 发布用：同时注入对外服务地址
node scripts/to-openapi-31.cjs api-docs.json out.json --server-url=https://yak-ops.example.com/yak-ops
```

脚本输出会打印：操作数、转换项计数、治理统计、`validation=ok`。
脚本同时接受 **3.0.x 导出文件**与**已治理的 3.1 契约**：前者做版本转换 + 治理，后者只做幂等治理（重复运行各项计数为 0）。

## Agent 白名单与一致性校验

`agent-allowlist.yaml` 是「agent 能看见哪些操作」的唯一事实源。新增/调整 agent 能力 = 修改该文件并走 PR 评审；`yak-ops-agent` 的工具注册表（`yak_agent/operations.yaml`）必须与白名单一一对应。

```bash
# 校验三方一致性（白名单 / 工具注册表 / 契约）
python scripts/check_agent_operations.py
```

校验项：operationId 在契约存在；白名单与工具注册表双向一一对应（无孤儿）；工具的 method + 归一化路径与契约一致；risk 等级一致；query 参数线上名在契约中存在。

## 升级脚本做了什么

| 规则 | 说明 |
| --- | --- |
| `openapi` | `3.0.x` → `3.1.0`，并补 `jsonSchemaDialect`（2020-12） |
| `nullable: true` | → `type: [<type>, "null"]`（无 type 时丢弃并计入 `nullableUnhandled`） |
| `exclusiveMinimum/Maximum: true` | → 数值形式（3.1 语义） |
| schema `example` | → `examples: [x]` |
| `type: file` | → `contentMediaType: application/octet-stream` |
| **鉴权补全** | 注入 `cookieAuth`（session cookie）与 `tokenAuth`（`token` 请求头，对齐 `LoginHandlerInterceptor`）+ 全局 `security` OR 数组，并对拦截器放行路径置 `security: []` |
| **tag 归一化** | 55 个三风格遗留 tag（`X_TAG` / `x related operation` / `x-controller`）映射为 52 个业务域 kebab-case；未命中打 `x-agent-unmapped-tag` |
| **summary 补齐** | 无 summary 的操作由 operationId 生成可读文本并标 `x-agent-summary-derived: true`；`summary-overrides.cjs` 的人工文案优先级最高（当前覆盖 18 条，derived 降至 169） |
| **servers 参数化** | `--server-url=URL` 注入对外地址；不传则保留导出时地址 |

当前基线实测：`nullable/exclusive/example/file` 转换项均为 **0**（现有注解未使用这些特性），
鉴权补全命中 8 个公开操作；治理结果：`tokenAuth=1`, `tagsNormalized=406`, `tagsUnmapped=0`, `summariesDerived=178`。

## 已知不足（后续治理项，非阻塞）

1. **169 条 summary 仍为派生文本**（`x-agent-summary-derived: true`，集中在 modeling/alert/users 等一期外域）——按需在 `summary-overrides.cjs` 增补人工文案即可，**不改上游 Controller**；
2. **operationId 存在泛名**（`instances`、`preview`、`publish`、`unpublish`、`version`、`overview` 等来自 data-sync 的方法名），
   若其它 Controller 出现同名方法将冲突——工具侧用语义化名称（白名单 `tool` 字段）规避，长期建议给方法改名或加 `@Operation(operationId=...)`；
3. `servers` 默认仍是导出时的运行时地址，发布集成包时用 `--server-url` 注入正式地址。

## 约束提醒

- 不要手工编辑 `yak-ops-3.1.json`（下次生成会覆盖），改动请落在注解或升级脚本上；
- `agent-allowlist.yaml` 可以手工维护（它就是人工审定的事实源）；
- 升级脚本零依赖（Node 内置模块），校验脚本依赖 PyYAML。
