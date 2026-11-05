# Frontend Tooling

The frontend uses Node.js 22.13+, npm workspaces and Vite. From `yak-ops-ui`:

```bash
npm ci
npm run dev
npm run format
npm run format:check
npm run build
npm run test:auth
```

`npm run format` applies Oxfmt formatting; `npm run format:check` verifies it, and `npm run check` is an alias for `npm run format:check`. Other developer commands, including `npm run lint` and `npm run typecheck`, remain available locally but do not run in GitHub Actions.

Frontend formatting is local-only. The focused `npm run test:auth` command checks the Java login form contract, user mapping, and response codes with Node's built-in test runner; it does not run in GitHub Actions. Browser and broader frontend regression tests are not part of the current project. The GitHub Actions workflows run Java checks only. A successful formatter or auth contract test does not prove production runtime behavior or UI accessibility.

## Java API development proxy

The Vite dev server proxies `/yak-ops/*` to the Java API, using `YAK_OPS_BACKEND_URL` (default `http://127.0.0.1:12345`). Password login, current-user lookup, and logout use this servlet context path directly. The existing `/api/*` proxy is unchanged and continues to use `YAK_BACKEND_URL` (default `http://127.0.0.1:8080`).

In production, serve the frontend on the same origin as the Java API or configure a reverse proxy for `/yak-ops/*`; the browser must send the server's HttpOnly session cookie on subsequent API calls. Do not put the session ID in localStorage.
