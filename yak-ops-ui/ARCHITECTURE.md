# Yak Ops UI Architecture

Status: Active

Scope:
- `yak-ops-ui/src/**`
- Current Datasource-only browser product

## Principle

Frontend code follows ownership, not file-type symmetry.

Current product navigation exposes Datasource only.

Do not reintroduce removed product domains through routes, menus, services or shared components.

## Current Structure

Yak Ops UI currently uses Umi Max + React + TypeScript.

Relevant Datasource owners:

```text
src/config/navigation.ts
→ product navigation metadata

src/pages/data-source/
→ Datasource page composition and page-owned interaction

src/services/data-source/
→ reusable Datasource backend API contracts

src/components/
→ reusable product UI / historical shared components

src/utils/
→ generic browser utilities
```

The repository still contains historical frontend code for removed domains. It is not an approved source of product scope or architecture.

## Dependency Direction

For Datasource work, prefer:

```text
page / page hook / page component
        ↓
Datasource service
        ↓
request / HTTP infrastructure
        ↓
Backend API
```

Shared UI/utilities may be used downward. Shared code must not import Datasource page behavior.

## Page Boundary

`src/pages/data-source` owns the Datasource product surface.

Page-level code may own:
- page composition
- local UI state
- Datasource-specific hooks
- Datasource-specific components

It must not:
- duplicate HTTP protocol handling already owned by Services
- create a second global navigation model
- move backend business truth into browser-only state

## Service Boundary

Reusable Datasource backend calls belong in `src/services/data-source`.

Existing page-local service files are current code and may remain until deliberately consolidated. Do not create duplicate endpoint clients.

## Navigation Boundary

`src/config/navigation.ts` is the current navigation source.

Only shipped capability should be visible.

Removed modules may retain historical page source temporarily, but they must not reappear in navigation unless product scope changes explicitly.

## State Boundary

- URL state stays in URL when it already expresses navigation.
- backend facts use server responses as truth.
- local interaction state stays at the smallest owner.
- do not add global state only to reduce prop passing for one page.

## Current Non-Goals

- no frontend-wide rewrite to match Yakable folders.
- no forced migration from Umi Max.
- no new state framework or query framework only for architecture symmetry.
- no deletion of historical frontend code as a side effect of Datasource feature work.

Architecture changes must solve a concrete problem.
