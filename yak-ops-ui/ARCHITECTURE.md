# Yak Ops UI Architecture

Status: Active

Scope: `yak-ops-ui/apps/**`、`yak-ops-ui/packages/**`。

系统模块边界见 [Repository Architecture](../ARCHITECTURE.md)。本文件只定义前端目录、职责和依赖；实现约束见 [Frontend Rules](FRONTEND_RULES.md)。

## Web Root Ownership

`apps/web` 是产品 Web Root，`packages/yak-ui` 是业务无关的共享 UI。

```text
apps/web/
  app/<domain>     产品页面、局部状态与展示
  app/router      URL 与产品入口装配
  app/layout      认证后共享 Shell
  service/<domain> 后端调用与请求/响应类型
  service/http    唯一 HTTP transport
  context         应用级运行态
  hooks           跨组件 Hook
  utils           无业务工具
  themes          应用主题
  types           跨 Web 稳定类型
  config          运行配置
  constants       稳定常量
  assets          参与构建的资源
  public          原样静态资源
packages/yak-ui/   共享组件、公共 Props 与视觉状态
scripts/          工具与架构检查
```

## Domain Locality

- `app/datasource`：数据源管理页面；就近约束见 [Datasource Rules](apps/web/app/datasource/DATASOURCE_RULES.md)。
- `app/management`：用户与工作空间管理；`app/login`：登录产品页面。
- 离线 / 实时同步与运维旧页面已删除；前端当前不提交同步任务。Data Sync 的后续页面重新实现前只保留原始 TypeScript DTO 类型定义。

## Dependency Direction

```text
app/router → app/layout / app/<domain>
app/<domain> → service/<domain> → service/http
app → @yak-ops/yak-ui → Base UI / DOM
```

后端请求/响应类型归 `service/<domain>/types.ts`。App 可以导入或重新导出；Service 不反向依赖 App。共享 UI 不依赖业务 Service。

## App Shell

[AppLayout](apps/web/app/layout/AppLayout.tsx) 拥有视口、Launcher 开关与 Workspace-scoped Outlet 生命周期；TopBar、ProductSidebar、ProductLauncher 和 AllProductMenu 归 `app/layout`。

产品 Registry 由 [navigation.ts](apps/web/app/layout/navigation.ts) 定义。当前数据集成只提供数据源管理入口，管理中心保持原有入口；偏好数据不定义产品标签、图标或路由。

Shell 的交互不变量由 [App Rules](apps/web/APP_RULES.md) 定义。页面内标题使用 [PageHeader](packages/yak-ui/docs/page-header.md)，不是第二个应用 TopBar；固定标题和局部滚动由页面布局负责。

## Service Boundary

[Service Rules](SERVICE_RULES.md) 定义 HTTP、类型与导出生命周期。数据源 Service 保留 Catalog 调用；旧数据同步 HTTP Service 已移除，当前只有 Data Sync TypeScript 类型定义。

## Architecture Enforcement

架构校验入口为 [check-architecture.mjs](scripts/check-architecture.mjs)。目录或依赖边界发生变化时，同时维护对应 Architecture、Rules 和已有检查；不能只删检查绕过边界。

## Verification

验证命令与工具归属见 [Frontend Tooling](docs/tooling.md)，不在每份架构文档重复维护命令清单。
