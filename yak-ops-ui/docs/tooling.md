# Frontend Tooling

Scope: `yak-ops-ui` 的工具、构建与验证。

## Toolchain

前端使用 Vite、React、React Router、Tailwind、TypeScript、Oxlint、Oxfmt 和 npm workspaces。Node 要求、依赖与脚本以 [package.json](../package.json)、workspace manifests 和 [package-lock.json](../package-lock.json) 为准。

Vite 负责开发和生产构建；npm workspaces 负责依赖安装，CI 使用 `npm ci`。架构校验入口是 [check-architecture.mjs](../scripts/check-architecture.mjs)，导入边界由 [architecture-imports.mjs](../scripts/architecture-imports.mjs) 检查，并使用 Node Test Runner 运行对应的回归测试。

## Local Verification

在 `yak-ops-ui/` 下运行：

```bash
npm ci
npm run check
npm run build
```

`npm run check` 依次验证 Oxfmt 格式、Oxlint 静态规则、TypeScript 类型及架构边界。生产构建由 Vite 独立完成。修改局部交互时另运行相应验证，不把静态检查视为浏览器验收。

## CI Quality

[Quality Check](../../.github/workflows/quality-check.yml) 按变更范围分别运行前端格式、Lint、类型、架构与构建检查；CI 检查源码但不自动修改格式。`scripts/ci/detect-changes.sh` 管理变更范围，`scripts/ci/test-detect-changes.sh` 验证范围规则。正式 Release Gate 仍执行完整质量检查。

## Runtime and Interaction Verification

Frontend PRs use static and TypeScript contract checks through [Quality Check](../../.github/workflows/quality-check.yml); the [Release Gate](../../.github/workflows/v1-release-gate.yml) runs Docker Compose authentication and HTTP smoke checks without browser automation.

The local [Playwright UI scenarios](../tests/e2e/README.md) remain available on demand. They are neither triggered by GitHub Actions nor required to pass a release.

Useful focused static scripts remain:

```bash
node scripts/verify-data-sync-definition.mjs
node scripts/verify-login-failure-transition.mjs
node scripts/verify-login-success-transition.mjs
```

Static scripts validate source contracts but do not prove actual DOM, focus, or animation behavior. Record the actual result when running optional local browser tests; an unrun or failed check must not be reported as passed.
