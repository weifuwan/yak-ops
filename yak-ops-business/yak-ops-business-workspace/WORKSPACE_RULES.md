# Workspace Rules

Scope:
- `yak-ops-business/yak-ops-business-workspace/**`
- Workspace persistence and HTTP changes

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- `/yak-ops-business/BUSINESS_RULES.md`
- `/docs/capabilities/workspace/README.md`
- DAO changes load `/yak-ops-dao/DAO_RULES.md`
- HTTP changes load `/CONTROLLER_RULES.md`

## Ownership

Workspace owns Workspace lifecycle currently required by the product, membership, membership validation for request context, and Workspace domain errors/constants.

Workspace does not own authentication. Current user identity comes from Security.

## Stable Service Boundary

```text
WorkspaceService
→ WorkspaceServiceImpl
```

Boot depends on `WorkspaceService`, never its implementation or Workspace Repository.

## Membership

V1 roles are OWNER, ADMIN and MEMBER.

Member-management rules:
- OWNER and ADMIN may add, update and remove ordinary members.
- only OWNER may grant OWNER or modify/remove an existing OWNER.
- a Workspace must always retain at least one OWNER.
- MEMBER has read access to member lists but cannot mutate membership.

These rules exist only for Workspace member management; do not expand them into a generic RBAC framework.

## Request Context

The HTTP header is `X-Workspace-Id`. Boot validates it against the current authenticated user before binding `WorkspaceContext`.

A missing header is globally valid. Workspace-scoped capabilities explicitly require context.

## Must

- create OWNER membership in the same transaction as Workspace creation.
- validate the target User before adding a WorkspaceMember.
- keep member mutation scoped by `workspaceId + userId`.
- derive current user ID from trusted authentication runtime.
- keep `workspaceId` out of ordinary resource create/update DTOs.
- use membership for access and audit fields only for audit.
- keep `app_name` and Workspace ownership separate.

## Must Not

- put Workspace membership into Security `UserEntity`.
- model resource ownership with `createBy`.
- add Organization / Team / Tenant aliases for the same concept.
- add invitation or RBAC infrastructure without a current caller.
- let Controller access Workspace Mapper / Repository directly.
