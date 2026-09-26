# Workspace Domain

Status: Active

Scope:
- Workspace creation and discovery
- Workspace membership
- Request workspace context
- Shared ownership boundary for future Yak Ops resources

## Principle

Workspace is the Yak Ops business data boundary.

```text
User
  ↓ membership
Workspace
  ↓ ownership
Resource
```

User answers "who am I". Workspace answers "where am I working". Resource answers "what exists in this workspace".

Security authentication and Workspace ownership are separate concerns. `yak_security_user.app_name` remains the Security application-isolation key and must not be reused as a Workspace ID.

## Current Owners

Backend:
- `yak-ops-business/yak-ops-business-workspace`

Persistence:
- `yak-ops-dao/src/main/java/io/yak/ops/dao/**/workspace`
- `yak-ops-dao/src/main/resources/db/migration/yak-ops`

HTTP:
- `yak-ops-boot/src/main/java/io/yak/ops/boot/controller/workspace`

Frontend:
- `yak-ops-ui/apps/web/context/workspace-context.tsx`
- `yak-ops-ui/apps/web/service/workspace`
- `yak-ops-ui/apps/web/app/layout/TopBar.tsx`

Request context:
- `io.yak.ops.common.context.WorkspaceContext`
- Header: `X-Workspace-Id`

## Core Model

```text
User N ─── N Workspace
       membership

Workspace 1 ─── N Resource
```

V1 persists the user/workspace relationship through `yak_ops_workspace_member`.

Workspace roles:
- `OWNER`
- `ADMIN`
- `MEMBER`

Membership access is shared by all Workspace members. Member mutation introduces the minimum role authorization required by the product: OWNER / ADMIN may manage members, but only OWNER may grant or modify OWNER membership, and the final OWNER cannot be removed or demoted.

## Invariants

1. A User may belong to multiple Workspaces.
2. A Workspace may contain multiple Users.
3. A future Workspace Resource must belong to exactly one Workspace.
4. Workspace-scoped Resource access must include `workspaceId`; querying a Workspace Resource by resource ID alone is forbidden.
5. `createBy / updateBy` are audit fields and never define resource ownership.
6. Business request DTOs must not freely choose `workspaceId`; the active Workspace is request context.
7. The active Workspace is carried by `X-Workspace-Id` and exposed through `WorkspaceContext`.
8. A supplied Workspace header is accepted only when the current authenticated User is a member of that Workspace.
9. Workspace selection endpoints do not require an existing Workspace context.
10. `app_name` is a Security application-isolation key, not a Workspace identifier.

## HTTP Contract

```text
POST /api/v1/workspaces
GET  /api/v1/workspaces
GET  /api/v1/workspaces/{id}
GET    /api/v1/workspaces/{id}/members
POST   /api/v1/workspaces/{id}/members
PUT    /api/v1/workspaces/{id}/members/{userId}
DELETE /api/v1/workspaces/{id}/members/{userId}
```

The current user identity always comes from `AuthenticationManager`; callers do not submit a user ID in Workspace DTOs.

For Workspace-scoped requests:

```http
X-Workspace-Id: <workspace-id>
```

Boot validates membership before binding the value to `WorkspaceContext`.

Missing `X-Workspace-Id` is allowed globally. A capability that requires Workspace scope calls `WorkspaceContext.requireWorkspaceId()`.

## Creation Contract

Creating a Workspace is transactional:

```text
create Workspace
      ↓
create OWNER membership for current User
      ↓
commit
```

A Workspace without its creating OWNER membership must never be committed.

## PR Boundaries

PR1 — Workspace Core + Contract:
- Workspace and WorkspaceMember schema
- DAO persistence
- Workspace Service
- Workspace HTTP APIs
- request Workspace context
- membership validation

PR2 — Datasource Workspace Ownership:
- add `workspace_id` to Datasource
- scope Datasource queries by Workspace
- change Datasource name uniqueness to `workspace_id + name`
- require Workspace context for Datasource operations

PR3 — Workspace Switcher + Datasource Integration:
- current Workspace state in UI
- Workspace switcher and lightweight Workspace creation
- HTTP client injects `X-Workspace-Id`
- auth/workspace-global requests explicitly omit Workspace header
- workspace-scoped product outlet remounts on Workspace switch

## Not In V1

- Organization / Tenant / Team hierarchy
- invitations
- member disable lifecycle
- generic role/permission matrix beyond Workspace member management
- Workspace deletion
- Workspace ownership transfer
- default-workspace migration for existing Datasource rows
