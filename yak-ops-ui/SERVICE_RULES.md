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
- UI 只拿业务 data，不解析后端统一 Result。
- 后端错误保留失败语义，不返回假成功数据。

## Must Not

- 从 `service/**` import `@/app/**`。
- Component、Page、Hook 直接调用 `fetch`。
- 创建 axios、umi-request 或第二套 transport。
- 让 HttpUtils 知道 Datasource 业务规则。
- 让 UI 感知 `Result<T>`。
- 为 Service 创建 interface / impl / adapter 层。
- 在 Service 保存页面 UI state。
- 从 Service import UI Component。

## Enforcement

以上稳定 invariant 由 `npm run architecture:check` 检查。
