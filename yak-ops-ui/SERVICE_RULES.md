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
= login / logout / current user

service/datasource
= Datasource CRUD / connection / plugin / catalog / driver
```

## Must

- Endpoint 由对应 Domain Service 拥有。
- 普通 HTTP 统一经过 `HttpUtils`。
- 原生 `fetch` 只允许存在于唯一 transport owner。
- HttpUtils 只负责 HTTP、统一 Result、JSON、网络错误和 transport 行为。
- Domain Service 负责 endpoint、参数、响应 Contract 和数据适配。
- UI 只拿业务 data，不解析后端统一 Result。
- 后端错误保留失败语义，不返回假成功数据。

## Must Not

- Component、Page、Hook 直接调用 `fetch`。
- 创建 axios、umi-request 或第二套 transport。
- 让 HttpUtils 知道 Datasource 业务规则。
- 让 UI 感知 `Result<T>`。
- 为 Service 创建 interface / impl / adapter 层。
- 在 Service 保存页面 UI state。
- 从 Service import UI Component。

## Boundary

Service 回答：

> 浏览器如何与后端通信？

App Domain 回答：

> 用户看到什么，以及用户操作如何组织？
