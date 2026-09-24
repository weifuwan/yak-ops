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

业务代码按 Domain 聚合，不再建立全局 `pages/`。

## Must

- Domain UI / state / model 放在 `app/<domain>`。
- Domain backend calls 放在 `service/<domain>`。
- 通用 UI 从 `@yak-ops/yak-ui` 使用。
- Router 只负责 URL → Product Surface 映射。
- Context 只拥有 App-wide runtime state。
- HTTP transport 只存在于 `service/http`。
- 原样静态资源进入 `public`；参与构建资源进入 `assets`。

## Must Not

- 重新创建 `src/` 或 `pages/`。
- 重新创建 `packages/datasource`。
- 创建 `@yak-ops/datasource` alias。
- 在 `utils/hooks/types/constants` 放 Domain 私有实现。
- 创建第二套 Theme Provider 或 HTTP Client。
- 因为方便而创建全局 Component / Utils 大桶。
