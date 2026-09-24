# Frontend Service Rules

Scope:
- `yak-ops-ui/apps/web/service/**`
- `yak-ops-ui/packages/datasource/src/api/**`

Depends On:
- `./ARCHITECTURE.md`
- `./FRONTEND_RULES.md`

## Flow

```text
Page / Feature
→ Domain Service / Domain API
→ apps/web/service/http
→ Backend API
```

## Ownership

```text
apps/web/service/http
= HTTP transport / Result envelope / network + auth failure handling

apps/web/service/auth
= login / logout / current user

packages/datasource/src/api
= Datasource endpoint + request/response adaptation
```

Datasource API → Web HTTP 是 PR1 到 PR2 的唯一临时 package bridge。

## Must

- 所有后端 endpoint 都由 Domain Service / Domain API 拥有。
- 普通 HTTP 统一经过 `HttpUtils`。
- 原生 `fetch` 只允许存在于唯一 transport owner。
- HttpUtils 只负责 HTTP、统一 Result、JSON、网络错误和 transport 行为。
- Domain Service 负责 endpoint、参数、响应 Contract 和业务数据适配。
- UI 只拿业务 data，不解析后端统一 Result。
- API Contract 类型与 owning Service 放在一起。
- 后端错误保留失败语义，不返回假成功数据。

## Must Not

- Component、Page、Hook 直接调用 `fetch`。
- 创建 axios、umi-request 或第二套 transport。
- 让 HttpUtils 知道 Datasource 业务规则。
- 让 UI 感知 `Result<T>`。
- 为 Service 创建 interface / impl / adapter 层。
- 在 Service 保存页面 UI 状态。
- 从 Service import Page / Feature 组件。

## Migration Rule

PR1 已删除历史根 `src/service/http`。

PR2 将 `packages/datasource/src/api` 与 Datasource Domain 一起迁入 Web App，并消除临时 package → Web transport bridge。
