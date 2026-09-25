# Frontend Service Rules

Scope:
- `yak-ops-ui/apps/web/service/**`

Depends On:
- `./ARCHITECTURE.md`
- `./FRONTEND_RULES.md`

## Flow

```text
App Domain
→ Domain Service
→ service/http
→ Backend API
```

## Ownership

```text
service/http
= HTTP transport / Result envelope / network + auth failure handling

service/auth
= login / logout / current user contract + endpoints

service/datasource
= Datasource contract + CRUD / connection
```

Backend request / response Contract 与对应 Service 放在一起。

## Service Locality

Service 同样优先局部内聚，不按 endpoint 名词机械拆文件。

默认 Domain Service 结构：

```text
service/<domain>/
├── index.ts
└── types.ts
```

- `index.ts`：该 Domain 的 endpoint、参数适配和轻量响应转换。
- `types.ts`：稳定的 backend request / response Contract。

只有形成独立 transport、独立协议、独立生命周期，或单文件复杂度已经明显影响阅读时，才继续拆 Service 文件。

不要因为存在 CRUD / Catalog 等概念，就默认创建 `api.ts / catalog.ts`。

## Dependency Invariant

Service 是 App 的下层。

```text
app → service
```

禁止：

```text
service → app
```

不要为了复用 `type` 破坏依赖方向。

例如 Datasource API Contract 的 owner 是：

```text
service/datasource/types.ts
```

App 可以 import / re-export Service Contract；Service 不得 import App model。

## Service Export Lifecycle

Frontend Service export 必须服务于当前 App 调用链，不把“后端存在的 API”机械镜像成前端函数。

Must:
- 新增 service function 时必须存在真实 App caller，或在当前功能 PR 中同时落地 caller。
- service function 删除后，同一变更中删除只为它存在的 request / response type、endpoint 常量和适配代码。
- 后端能力暂时没有前端产品入口时，可以保留后端 Contract，但前端不提前创建占位 service export。
- 审查 service 时以 `app → service` 的真实引用为准，不以“以后可能会用”作为保留理由。

Must Not:
- 为每个后端 Controller 方法自动创建同名 frontend service。
- 保留全仓只有定义、没有 App caller 的 export。
- 保留只被死 service function 使用的 TypeScript interface / type。
- 为未来页面提前维护 URL prefix、response type 或 adapter。

## Must

- Endpoint 由对应 Domain Service 拥有。
- 普通 HTTP 统一经过 `HttpUtils`。
- 原生 `fetch` 只允许存在于唯一 transport owner。
- HttpUtils 只负责 HTTP、统一 Result、JSON、网络错误和 transport 行为。
- Domain Service 负责 endpoint、参数、响应 Contract 和数据适配。
- 同一个 Domain 的轻量 endpoint 优先保持在一个 Service entry 中。
- 稳定 backend Contract 可以独立放在 `types.ts`。
- UI 只拿业务 data，不解析后端统一 Result。
- 后端错误保留失败语义，不返回假成功数据。

## Must Not

- 从 `service/**` import `@/app/**`。
- Component、Page、Hook 直接调用 `fetch`。
- 创建 axios、umi-request 或第二套 transport。
- 让 HttpUtils 知道 Datasource 业务规则。
- 让 UI 感知 `Result<T>`。
- 为 Service 创建 interface / impl / adapter 层。
- 为 CRUD / Catalog 等概念创建只有少量代码的 Service 文件。
- 在 Service 保存页面 UI state。
- 从 Service import UI Component。

## Enforcement

以上稳定 invariant 由 `npm run architecture:check` 检查。
