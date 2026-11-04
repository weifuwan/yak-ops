# Frontend Tooling

The frontend uses Node.js 22.13+, npm workspaces and Vite. From `yak-ops-ui`:

```bash
npm ci
npm run dev
npm run format
npm run format:check
npm run build
```

`npm run format` applies Oxfmt formatting; `npm run format:check` verifies it, and `npm run check` is an alias for `npm run format:check`. Other developer commands, including `npm run lint` and `npm run typecheck`, remain available locally but do not run in GitHub Actions.

Frontend formatting is local-only. Frontend browser, Node, architecture regression and contract tests have been removed. The only remaining GitHub Actions workflow, [Backend Tests](../../.github/workflows/backend-tests.yml), tests Java code only. A successful formatter does not prove compilation, runtime behavior, or UI accessibility.
