# Web App Rules

Scope:
- `yak-ops-ui/apps/web/**`

Owns:
- Browser application composition
- Router
- Application providers
- Application layout
- Login page
- Global styles

## Must

- App 只组合产品能力，不拥有 Datasource 业务实现。
- Datasource 页面只通过 `@yak-ops/datasource` 进入 App。
- 通用 UI 只从 `@yak-ops/yak-ui` 使用。
- Router 只负责 URL → Product Surface 映射。
- Provider 只拥有应用级运行时状态。

## Must Not

- 直接导入 `@base-ui/react`。
- 在 App 内实现 Datasource Card、Editor、Connection、Plugin 等业务组件。
- 从 `src/pages/data-source` 或 `src/service/datasource` 直接导入业务实现。
- 因为方便而重新创建全局 Component / Utils 大桶。

## Boundary

```text
apps/web
   ↓
packages/datasource
   ↓
packages/yak-ui
```

PR1 只迁移 App owner，不改变 Datasource 用户行为。
