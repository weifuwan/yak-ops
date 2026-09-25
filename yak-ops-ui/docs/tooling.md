# Frontend Tooling

Status: Active

Scope:
- `yak-ops-ui`

## Toolchain

当前前端工具链：

```text
Vite 6
React 18
React Router
Tailwind CSS 4
TypeScript 5.9
Oxlint
Oxfmt
npm
Node architecture check
```

Node 要求：

```text
>= 22.13
```

Umi Max、Biome、Yarn、Husky、lint-staged、commitlint 不属于当前前端工具链。

## Required Checks

本地在 `yak-ops-ui/` 下执行：

```bash
npm run check
npm run build
```

`check` 是本地聚合入口，按下面顺序执行四个确定性 quality gate：

```text
format:check
      ↓
lint
      ↓
typecheck
      ↓
architecture:check
```

Build 独立执行，验证 Vite 真实生产构建。

GitHub Actions 不使用单一 `npm run check` 步骤，而是直接执行每个 gate，让失败原因在 CI 中独立可见：

```text
Frontend format
      ↓
Frontend lint
      ↓
Frontend type check
      ↓
Frontend architecture
      ↓
Frontend build
```

## Commands

```bash
npm run dev
npm run build
npm run preview
npm run architecture:check
npm run typecheck
npm run lint
npm run lint:fix
npm run format
npm run format:check
npm run check
```

## Physical Quality Gate

前端中能由工具确定判断的规则由确定性工具负责，不使用 AI 做全文格式判断。

所有 CI gate 都是 check-only：

- Oxfmt 负责格式，CI 执行 `npm run format:check`。
- Oxlint 负责静态代码规则，CI 执行 `npm run lint`。
- TypeScript 负责类型正确性，CI 执行 `npm run typecheck`。
- Node architecture check 负责仓库架构边界，CI 执行 `npm run architecture:check`。
- Vite 负责生产构建验证，CI 执行 `npm run build`。

CI 禁止执行会修改源码的命令：

```text
npm run format
npm run lint:fix
```

格式或 lint 不通过时直接失败，由开发者在本地修复后重新提交。

## Architecture Check

```bash
npm run architecture:check
```

由 `scripts/check-architecture.mjs` 执行。

它保护当前稳定边界，包括：

- 禁止恢复 `src / pages / shared / packages/datasource` 等遗留目录。
- `packages` 当前只允许 `yak-ui`。
- Workspace root 不拥有运行时依赖。
- 禁止恢复 `@yak-ops/datasource`。
- 禁止恢复 Ant Design / Umi / 第二套 HTTP Client。
- `service/**` 禁止反向依赖 `app/**`。
- `app/**` 禁止直接依赖 `service/http`。
- 原生 `fetch` 只允许存在于唯一 HTTP transport owner。
- `@base-ui/react` 只允许由 `packages/yak-ui` 使用。

如果架构需要演进，应先修改 Architecture / Rules，再修改 enforcement。不要通过删检查规则绕过边界。

## Typecheck

TypeScript 是静态类型正确性的 owner：

```bash
tsc --noEmit
```

检查覆盖 `apps / packages`。

不要用 lint 替代 typecheck。

## Lint

Oxlint 拥有静态代码规则。

```text
apps
packages
scripts
```

- warning 按失败处理。
- 优先修 owner 问题，不使用 broad disable。
- 必须 disable 时只做最小范围并说明原因。
- 不再新增 Biome / ESLint 作为第二套 lint owner。

## Format

Oxfmt 拥有 TypeScript / TSX / JavaScript / tooling scripts 格式。

格式检查与 lint 分离。

不要手工维护与 Oxfmt 冲突的格式规则。

## Build

Vite 是唯一前端开发与构建入口。

```bash
npm run dev
npm run build
```

禁止重新添加：

- `max dev`
- `max build`
- Umi config
- Umi route config
- Umi runtime model

## Tailwind

Tailwind CSS 4 通过 Vite Plugin 接入。

不要恢复 Tailwind 3 PostCSS pipeline 或第二套 Utility CSS framework。

## Package Manager

前端命令统一使用 npm。

根 `package.json` 使用 npm workspaces：

```text
apps/*
packages/*
```

运行时依赖由真实 workspace owner 声明：

```text
apps/web
→ React / Router / Lucide / Yak UI

packages/yak-ui
→ Base UI / CVA
```

Workspace root 只保留构建和质量工具，不声明运行时 dependencies。

前端提交 `package-lock.json` 作为 npm 依赖锁定文件。新增或修改依赖后必须同步更新 lockfile；CI 使用 `npm ci` 按 lockfile 安装依赖。

## Git Hooks

当前不维护 Husky / lint-staged / commitlint gate。

不要保留“依赖已经删除但 hook 还存在”的假门禁。

本地质量入口统一是：

```bash
npm run check
npm run build
```

CI 则直接运行独立 physical quality gates，不把聚合命令作为黑盒门禁。

## Tests

当前 Yak Ops UI 没有重新建立前端测试 gate。

需要测试时单独定义测试 Contract 和 Tooling，再进入前端质量体系。

## Verification Record

提交或 PR 中只记录实际执行过的检查。

如果环境无法执行，应明确说明原因，不得写没有实际执行的 “CI passed / build passed”。
