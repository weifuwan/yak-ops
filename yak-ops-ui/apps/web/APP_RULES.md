# Web App Rules

Scope:
- `yak-ops-ui/apps/web/**`

Owns:
- Browser application composition
- Router
- Application providers / context
- Application layout
- Login page
- Web service infrastructure
- Theme
- Web assets and public files

## Root Structure

```text
app/
public/
service/
utils/
themes/
types/
hooks/
context/
config/
constants/
assets/
```

`apps/web` 本身就是 Web Root，禁止再创建 `src/`。

## Must

- App 只组合产品能力，不把业务实现塞进基础设施目录。
- 通用 UI 只从 `@yak-ops/yak-ui` 使用。
- Router 只负责 URL → Product Surface 映射。
- Context 只拥有 App-wide runtime state。
- HTTP transport 只存在于 `service/http`。
- Theme Token 只存在于 `themes`。
- 原样静态资源进入 `public`；参与构建的资源进入 `assets`。

## Must Not

- 直接导入 `@base-ui/react`。
- 重新创建 `src/`。
- 在 `utils/hooks/types/constants` 放某个 Domain 私有实现。
- 创建第二套 Theme Provider 或 HTTP Client。
- 因为方便而创建全局 Component / Utils 大桶。

## Migration Boundary

PR1 保留 `packages/datasource`，仅允许其 API 临时复用 `service/http`。

PR2 再把 Datasource 收口到 `app/datasource`，不在本 PR 提前改变业务行为。
