# Frontend Service Rules

Scope:
- `yak-ops-ui/src/service/**`

Depends On:
- `./ARCHITECTURE.md`
- `./FRONTEND_RULES.md`

Owns:
- Backend API 调用
- HTTP 协议边界
- 请求 / 响应 Contract
- Datasource / Auth endpoint

## Flow

固定调用方向：

```text
Page / Feature
→ Domain Service
→ HttpUtils
→ request transport
→ Backend API
```

## Target Ownership

目标目录：

```text
src/service/
├── http/
├── auth/
└── datasource/
```

Login 已完成 Auth Service 迁移到 `src/service/auth`。

Datasource 已完成迁移到 `src/service/datasource`。

旧 `src/services/data-source` 已删除，不得重新建立兼容出口。

其它 `src/services/security` 历史管理代码不属于当前 Login / Datasource 运行边界，不得作为新代码依赖。

## Must

- 所有后端 endpoint 都由 Domain Service 拥有。
- Datasource API 收口到 Datasource Service。
- Login / Logout / Current User 收口到 Auth Service。
- 普通 HTTP 统一经过 `HttpUtils`。
- 原生 `fetch` 只允许存在于唯一 transport owner。
- HttpUtils 只负责 HTTP、后端 Result、JSON、网络错误和通用 transport 行为。
- Domain Service 负责 endpoint、参数、响应 Contract 和业务数据适配。
- UI 只拿业务 data，不解析后端统一 Result。
- API Contract 类型与 owning Service 放在一起。
- 方法名表达后端业务语义，不使用 `requestXxx / callXxx` 这类 transport 命名。
- `AbortSignal` 有需要时由调用方传入并保持取消语义。
- 新接口优先扩展已有 Domain Service，不创建平行 client。
- 后端错误必须保留失败语义，不返回假成功数据。

## Must Not

- Component、Page、Hook 直接调用 `fetch`。
- 在 Page 重复定义已有 API Contract。
- 让 HttpUtils 知道 Datasource 业务规则。
- 让 UI 感知 `Result<T>` 包装。
- 新增 axios、umi-request 或其它第二套 transport。
- 为 Service 创建 interface / impl / adapter 层。
- 在 Service 保存页面 UI 状态。
- 从 Service import Page / Feature 组件。
- 新增 page-local endpoint client。
- 为已删除产品域恢复 Service。

## Result Boundary

后端统一响应：

```text
Result<T>
```

只在 HTTP infrastructure 内处理。

Domain Service 对上层返回：

```text
T
Promise<T>
```

不要让 Page 出现：

```text
response.data.data
response.code
response.msg
```

## Auth Boundary

Auth Service 只拥有：

- login
- logout
- current user
- 当前认证 Contract

认证状态本身由 `app/providers` 持有。

Auth Service 不拥有 React state。

## Datasource Boundary

Datasource Service 拥有：

- Datasource CRUD
- Connection Test
- Plugin Config
- Driver Upload
- Catalog API
- 对应请求 / 响应 Contract

Datasource Page / Feature 不拼接 endpoint URL。

## Migration Rule

迁移顺序：

```text
确认 owner
→ 搬 Service Contract
→ 修改真实调用方
→ 删除旧出口
```

不要先复制一份再长期保留两套。

## Boundary

Service 拥有“浏览器如何与后端通信”。

Service 不拥有“页面怎么展示”和“用户当前怎么交互”。
