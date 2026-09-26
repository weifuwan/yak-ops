# Management Center Rules

Scope:

- `apps/web/app/management/**`
- `apps/web/service/user/**`
- Workspace management UI integration

Depends On:

- `/yak-ops-ui/FRONTEND_RULES.md`
- `/yak-ops-ui/apps/web/APP_RULES.md`
- `/yak-ops-ui/ARCHITECTURE.md`

## Ownership

Management Center is a system-scoped product surface.

It owns:

- User management UI
- Workspace management UI
- Workspace member read and management presentation

It does not own:

- authentication runtime
- Datasource
- Workspace request context
- role/RBAC rules not exposed by backend contracts

## Structure

```text
app/management/
├── users/
│   ├── index.tsx
│   └── form.tsx
├── workspaces/
│   ├── index.tsx
│   └── form.tsx
└── index.tsx

service/
├── user/
│   ├── index.ts
│   └── types.ts
└── workspace/
    ├── index.ts
    └── types.ts
```

## UI

Must:

- reuse Yak UI `PageHeader / Table / Button / Input / Field / Modal / Dialog / Textarea / PasswordInput`.
- use the same `PageHeader → white content panel → toolbar → table` page rhythm as Datasource management.
- keep backend contracts under `service/<domain>`.
- omit Workspace request headers for system-scoped User APIs and Workspace discovery/management APIs.
- only show actions backed by current backend contracts.
- Workspace member management reuses Yak UI Table / Select / Modal / Dialog / Input and the existing User search API.
- MEMBER sees member lists as read-only; OWNER / ADMIN mutation visibility mirrors backend authorization.

Must Not:

- create page-local copies of Table, Modal, Pagination or Form primitives.
- add Workspace edit/delete actions before matching backend contracts exist.
- introduce a second HTTP client.
- put management APIs under the Datasource service.
