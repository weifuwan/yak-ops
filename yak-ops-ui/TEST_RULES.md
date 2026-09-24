# Frontend Test Rules

Scope:
- `yak-ops-ui/src/**/*.test.ts`
- `yak-ops-ui/src/**/*.test.tsx`
- frontend behavior that needs regression evidence

Runtime:
- Jest
- jsdom
- React Testing Library

Depends On:
- `./FRONTEND_RULES.md`
- nearest owner rules

## When Tests Are Required

优先保护：

- Datasource create/edit/delete user flow logic.
- loading / success / error / empty states.
- connection form behavior and plugin-driven form state.
- datasource status presentation with business meaning.
- catalog / driver management interactions when behavior is stable.
- route/navigation behavior.
- stable utility transformations.
- regression bugs.

默认不机械测试：

- CSS/LESS 样式细节。
- 颜色、圆角、margin、padding。
- Icon path。
- 内部 state/ref/effect 实现。
- 只断言“组件能渲染”。
- 只为了覆盖率数字。

## Boundary

### Page / Component / Hook

- 通过用户可观察行为验证。
- 可以 Mock Service。
- 不直接验证底层 HTTP 库。

### Service

- Mock network/request boundary.
- 验证 endpoint、method、参数和必要 response mapping。

### Utility

- 只保护稳定输入输出。
- 简单透传不补无价值测试。

## Query

优先使用语义查询：

```text
getByRole
→ getByLabelText
→ getByText / getByPlaceholderText
→ getByTestId
```

只有没有合理语义时才使用 test id。

## Stability

- 不访问真实网络。
- 不依赖测试执行顺序。
- 不使用固定 sleep 等 UI。
- 不依赖真实时间或随机结果，除非时间本身就是 Contract。

## Execution

```bash
cd yak-ops-ui
yarn test
yarn lint
yarn build
```

当前 CI 只执行 `yarn build`，因此测试结果需要在 PR 中明确记录。
