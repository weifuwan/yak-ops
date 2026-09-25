# Web App Rules

Scope:

- `yak-ops-ui/apps/web/**`

Owns:

- Product domains
- Router / Layout
- Application context
- Backend services
- Theme
- Web assets and public files

## Root Structure

```text
app/
service/
public/
utils/
themes/
types/
hooks/
context/
config/
constants/
assets/
```

`apps/web` 本身就是 Web Root。

## App Domain

```text
app/datasource
app/login
```

业务代码按 Domain 聚合，不建立全局 `pages / shared / components` 大桶。

## App Shell

```text
app/layout/
├── AppLayout.tsx
├── TopBar.tsx
├── ProductSidebar.tsx
├── ProductLauncher.tsx
└── navigation.ts
```

- `AppLayout` 是认证后产品页面唯一的 viewport owner，也是 Global Product Launcher 状态 owner。
- TopBar、ProductSidebar、ProductLauncher 属于 Shell，不属于 Datasource Domain。
- 产品内导航和全局产品菜单统一读取 `app/layout/navigation.ts`，不要维护两套菜单常量。
- ProductLauncher 必须作为 overlay 覆盖页面，打开和关闭都不能改变 Sidebar / Outlet 的宽高布局。
- Launcher 必须支持关闭按钮、背景点击、Escape 和路由变化关闭。
- Launcher 只展示已有真实路由的 Product Surface，不为视觉完整性创建假路由、禁用占位菜单或空页面。
- AppLayout 内的页面只填充可用容器，禁止通过 `calc(100vh - ...)` 或 `calc(100dvh - ...)` 自己扣减 Shell 高度。

## Must

- Domain UI / state / presentation 放在 `app/<domain>`。
- Domain backend Contract / calls 放在 `service/<domain>`。
- 依赖方向保持 `app → service → http`。
- 通用 UI 从 `@yak-ops/yak-ui` 使用。
- Router 只负责 URL → Product Surface 映射。
- Context 只拥有 App-wide runtime state。
- HTTP transport 只存在于 `service/http`。
- 原样静态资源进入 `public`；参与构建资源进入 `assets`。

## Must Not

- 重新创建 `src / pages / shared`。
- 重新创建 `packages/datasource`。
- 创建 `@yak-ops/datasource` alias。
- Service 反向 import App。
- App 直接 import `service/http`。
- 在 `utils/hooks/types/constants` 放 Domain 私有实现。
- 创建第二套 Theme Provider 或 HTTP Client。

## Enforcement

稳定目录和依赖方向由 `npm run architecture:check` 检查。
