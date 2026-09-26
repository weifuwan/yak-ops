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
app/management
├── users
└── workspaces
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
├── AllProductMenu.tsx
└── navigation.ts
```

- `AppLayout` 是认证后产品页面唯一的 viewport owner，也是 Global Product Launcher 状态 owner；通过 Product 参数服务数据集成和管理中心，不复制第二套 Shell。
- TopBar、ProductSidebar、ProductLauncher 属于 Shell，不属于 Datasource 或 Management Domain。
- 数据集成是 Workspace-scoped Product；管理中心是系统级 Product，不显示 Workspace Switcher，也不受 current Workspace gate。
- 产品内导航和全局一级产品项统一读取 `app/layout/navigation.ts`，不要维护两套真实产品常量。
- `所有产品` 是 ProductLauncher 自己的 `view-all` 入口，不允许混进真实产品数组；点击后只控制二级 AllProductMenu。
- ProductLauncher 使用固定宽度 220px 的 Launcher Track；位移动画必须作用在 Track，而不是只作用在一级菜单。打开使用 `translateX(-220px) → translateX(0)` 和 `300ms ease-in-out`，关闭使用 `220ms ease-in-out`。
- ProductSidebar 默认背景固定 `#FAFAFA`；二级深色面板关闭时不得残留覆盖默认 Sidebar。
- ProductSidebar 菜单项使用整行布局，不使用圆角卡片；选中态背景为 `#DFE6FA`，右侧使用 `#1645D1` 2px 高亮边。
- ProductSidebar 非选中项 hover 背景为 `#F2F2F2`；菜单图标保持 `#1645D1`，文字保持深色。
- AllProductMenu 必须作为 Launcher Track 的绝对定位子元素，通过 `left: 100%` 紧贴一级菜单右边缘；打开宽度 765px、关闭宽度 0，通过 `overflow: hidden` 裁切内容；打开动画 `240ms ease-in-out`，关闭动画 `170ms ease-in-out`，背景固定 `#1c1e21`。
- 完整 Launcher 关闭时，二级立即开始收缩，Launcher Track 延后 36ms 开始左滑；这是短暂错峰而不是等待二级完全结束后再关闭一级。两级必须共享 Track 位移，关闭全过程一级右边缘与二级左边缘保持相连，不允许出现中间空白。
- 一级 `view-all` 在二级展开时使用 `#1c1e21` 激活背景；一级 / 二级可点击产品 hover 使用 `#282b2e`，文字与图标同步提亮。
- 两级菜单都作为 overlay 覆盖页面，不允许改变 Sidebar / Outlet 布局，也不允许添加外层阴影。
- TopBar 三杠菜单按钮必须显示 pointer cursor；打开后同一位置切换为 X 图标。
- Launcher 必须支持 TopBar X、Escape 和路由变化关闭。
- Launcher 打开时，页面内容区覆盖透明 Blank Area 捕获点击；无论二级是否打开，一次 Blank Area 点击都关闭完整 Launcher，视觉上仍由二级先收、一级紧跟。
- 二级 AllProductMenu 只展示已有真实产品 / 路由；当前真实产品为数据集成和管理中心。
- 管理中心 V1 路由为 `/management/users` 与 `/management/workspaces`；PR1 只建立 Product Surface 与导航壳，具体管理能力由后续 Capability PR 实现。
- AppLayout 内的页面只填充可用容器，禁止通过 `calc(100vh - ...)` 或 `calc(100dvh - ...)` 自己扣减 Shell 高度。

## Must

- Domain UI / state / presentation 放在 `app/<domain>`。
- Management Center 的用户与工作空间页面收口在 `app/management/users` 与 `app/management/workspaces`，后端调用分别进入 `service/user` 与 `service/workspace`。
- Domain backend Contract / calls 放在 `service/<domain>`。
- 依赖方向保持 `app → service → http`。
- 通用 UI 从 `@yak-ops/yak-ui` 使用。
- 管理型新增 / 编辑表单遵循 `FORM_RULES.md` 的 Compact Horizontal Form Contract。
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
