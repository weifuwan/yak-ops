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
```

Node 要求：

```text
>= 22.13
```

Umi Max、Biome 和 Yarn 不再是当前脚手架工具链。

## Required Checks

在 `yak-ops-ui/` 下执行：

```bash
npm run check
npm run build
```

当前 `check` 包含三个独立 gate：

```text
typecheck
   ↓
lint
   ↓
format:check
```

Build 独立执行，验证 Vite 真实生产构建。

## Commands

```bash
npm run dev
npm run build
npm run preview
npm run typecheck
npm run lint
npm run lint:fix
npm run format
npm run format:check
npm run check
```

## Typecheck

TypeScript 是静态类型正确性的 owner：

```bash
tsc --noEmit
```

不要用 lint 替代 typecheck。

当前检查覆盖 `apps / packages / src migration bridge`。PR1 之后 `src/**` 不再是长期产品 owner，但在 Datasource 迁移完成前仍必须进入 typecheck / lint / format。

## Lint

Oxlint 拥有静态代码规则。

- warning 按失败处理。
- 优先修 owner 问题，不使用 broad disable。
- 必须 disable 时只做最小范围并说明原因。
- 不再新增 Biome / ESLint 作为第二套 lint owner。

## Format

Oxfmt 拥有 TypeScript / TSX / Vite 配置格式。

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

不要恢复：

- Tailwind 3 PostCSS pipeline。
- 旧 `tailwind.config.js` 只为兼容历史写法。
- 第二套 Utility CSS framework。

## Package Manager

前端命令统一使用 npm。

根 `package.json` 使用 npm workspaces：

```text
apps/*
packages/*
```

PR1 不切换 pnpm / yarn。

新增或修改依赖后应使用 npm 更新 lockfile。

不要同时维护 npm / yarn 两套前端依赖事实来源。

## Dependency Policy

新增依赖前先回答：

- 浏览器 / React / 当前依赖是否已经提供？
- 是否解决当前真实问题？
- 是否引入第二个 owner？
- 删除它是否会改变业务 Contract？

没有明确必要性，不新增依赖。

尤其不要默认引入：

- Zustand。
- Redux。
- TanStack Query。
- 第二套路由。
- 第二套 HTTP Client。
- 第二个 linter / formatter。

## Tests

当前 Yak Ops UI 没有重新建立前端测试 gate。

不要在架构 / 脚手架 PR 中顺手恢复历史测试体系。

需要测试时单独定义测试 Contract 和 Tooling，再进入 `npm run check`。

## Verification Record

提交或 PR 中只记录实际执行过的检查。

允许：

```text
npm run typecheck: passed
npm run build: passed
```

如果环境无法执行，应明确说明原因。

禁止写没有实际执行的“CI passed / build passed”。
