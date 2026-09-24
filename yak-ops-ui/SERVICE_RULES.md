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
= Datasource contract + CRUD / connection / plugin / catalog / driver
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

不要因为存在 CRUD / Catalog / Driver / Plugin 等概念，就默认创建 `api.ts / catalog.ts / driver.ts / plugin.ts`。

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
- 为 CRUD / Catalog / Driver 等概念创建只有少量代码的 Service 文件。
- 在 Service 保存页面 UI state。
- 从 Service import UI Component。

## Enforcement

以上稳定 invariant 由 `npm run architecture:check` 检查。
