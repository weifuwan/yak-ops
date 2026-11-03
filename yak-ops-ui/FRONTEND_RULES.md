# Frontend Rules

Scope: `yak-ops-ui/apps/**`, `yak-ops-ui/packages/**`, and `yak-ops-ui/scripts/**`.

This document defines durable frontend engineering constraints. Current source code, TypeScript types, tests, and backend APIs are the authority for observable behavior. See [Frontend Tooling](docs/tooling.md) for verification commands and the [YOIP process](../.github/CONTRIBUTING.md#yak-ops-improvement-proposals-yoips) for major design changes.

## Ownership and Dependencies

- Use the current feature contract directly. Do not keep compatibility re-exports, obsolete editor state, alias props, duplicate mappers or fallback UI paths without a supported consumer and explicit removal plan. Reject incompatible stored data instead of silently converting it.


- `apps/web` owns the product application. `packages/yak-ui` owns domain-agnostic controls; `packages/yak-ui/src/index.ts` defines their public API.
- `apps/web/app/<domain>` owns domain pages and interaction state. `app/layout` owns the shell; `app/router` owns route composition and historical URL redirects. `app/data-sync` is the unified offline/realtime synchronization feature.
- Product requests flow from `app/<domain>` to `service/<domain>` to `service/http`. The HTTP transport is centralized, including consistent response and authentication error handling. Domain services own endpoint adaptation and request/response types and must never depend on App code.
- UI dependencies flow from App to `@yak-ops/yak-ui`, then to Base UI/DOM. Yak UI must not contain product routes, Workspace policy, business copy, or domain data access.
- Organize components, hooks, local types, and tests by real user workflow. Promote cross-domain code only after actual stable reuse. Do not create speculative `common` or `shared` layers.
- Workspace-scoped queries must isolate user and Workspace identity. Pages must not invent Workspace headers; transport applies the real API contract.
- Services expose only capabilities with real consumers. Components must not parse HTTP envelope details or turn API failures into successful empty results.

## Feature Ownership and Components

- Feature directories own their behavior. Route entrypoints should connect routing to the owning feature, rather than forwarding exports through one-line compatibility files.
- In Data Sync, `list/`, `create/`, `editor/`, `detail/`, `execution/`, `detail/log/`, and `solution/` describe responsibilities. `components/` is reserved for genuinely reused domain controls. Keep a module at the lowest level that owns every actual consumer.
- Route composition may import a feature page entrypoint. Other domains may not deep-import another feature implementation; do not create permanent forwarding modules simply to preserve an obsolete internal import path.
- In Schedule, `dialog/` owns editing and write-recovery sessions, `form/` owns Cron configuration, and `history/` owns trigger filters and records. Public schedule controls expose explicit entrypoints; private query internals remain within Schedule.
- Schedule server reads, previews, and fire history use user/Workspace-scoped TanStack Query with visibility-aware polling. Unknown write outcomes must require an explicit reload; do not silently retry revisions or rebase a dirty draft.
- In Operations, `dashboard/` owns range selection, user/Workspace-scoped queries, summary cards, trend/status charts and recent failures. The router owns historical instance redirects; do not maintain pass-through page exports.
- In App Shell, `TopBar` composes navigation; `account-menu` handles sign-out, `workspace-switcher` handles selection, `workspace-create-dialog` owns creation, and `ProductLauncher` owns menu interaction. Product favorites belong to a user-scoped Query, not a page-owned fetch cache. The Shell cannot import product feature implementations.
- Router can import `AppLayout` and `navigation` but not private App Shell modules. Preserve supported historical redirects in the router, not in feature page forwarding files. Deleted compatibility entrypoints must not return.

- The smallest behavior owner handles its loading, empty/error states, actions, and lifecycle. Parent components coordinate only genuinely shared selection, navigation, or submission state.
- Split components based on behavior and rendering boundaries, not merely line count. Review responsibility when a file exceeds 400 lines; justify files above 600 lines during review. These are review signals, not automatic failure thresholds.
- Never replace a huge page with an equally huge `usePageModel` or `useEditor` hook. Hooks must own an identifiable query, subscription, or interaction. Avoid pass-through hooks.
- Product pages use Yak UI public controls rather than replicating base control behavior. Pass meaningful domain types and identities through props; do not store values that are derivable from existing state.
- Keep loading, empty, and error presentation near its data consumer. Skeletons should preserve final layout rather than introducing an unrelated full-page loading state.

## State, Queries, and Effects

- Local interactions such as dialog visibility, drafts, input, and selection belong to the relevant lifecycle owner. Shareable filters, pagination, tabs, and instance selection belong in the URL, with no parallel independent identity state.
- Server data belongs in the shared TanStack Query cache, not a page-owned `useState + useEffect` cache. Query keys must include every relevant parameter and user/Workspace scope.
- Keep API and version adaptation in services. Query and mutation ownership should be near consumers; invalidate relevant queries after mutations rather than creating duplicate refresh counters.
- Use a shared store only when sibling components need a common authority or state must survive a workflow unmount. Do not introduce a global store for component nesting alone.
- `useEffect` is for external synchronization, subscriptions, timers, and DOM integration. Do not use it to derive render state, handle submissions, or mirror query results. Polling must stop correctly after a state change, unmount, or visibility transition.
- Forms own defaults, validation, dirty state, and payload creation. A single submission flow coordinates Task Definition, Schedule, Publish, and partial failure recovery.
- Never disguise failed or unknown requests as success or empty data. Request cancellation, stale responses, retry behavior, and final feedback must have clear owners. Keep existing semantics until a migration is proven equivalent.

## Tailwind and CSS Boundaries

- Use Tailwind CSS v4 for ordinary business layout and interactions. Prefer existing Yak UI semantic tokens and state selectors; avoid repeated fixed colors such as `text-[#...]` or `bg-[#...]`.
- Prefer standard utility classes and semantic `data-*` attributes. Keep feature-scoped CSS only when it is appropriate for dynamic geometry, specialized keyframes, third-party DOM overrides, or log syntax coloring. Migrate existing CSS deliberately, not with blind deletion.
- Feature CSS must not override generic Yak UI controls or depend on private styles owned by another domain. Support light/dark themes and `prefers-reduced-motion`; derive theme variables from existing tokens.
- Do not move a hardcoded color from `className` to inline `style` to bypass checks. Inline styles are for genuinely runtime-dependent values. Add reusable visual capabilities in `packages/yak-ui`.
- The architecture check enforces semantic colors in migrated Data Sync `list/`, `editor/`, and `detail/` modules. Dedicated log coloring and execution animation CSS remain valid where the feature owns them.

## Testing and Architecture Enforcement

- Keep pure functions and transformations under focused unit tests. Test component input, async states, focus, and error behavior at the component boundary; validate cross-page behavior with focused manual checks or optional local browser automation when needed.
- Protect Data Sync draft restoration, dependent field resets, validation, saving, Schedule partial failure, publish failure, Workspace switching, and instance polling during refactors. Never weaken assertions merely to complete a move.
- `scripts/check-architecture.mjs` enforces real dependency direction: services cannot import App code, pages cannot bypass domain services for HTTP, and Yak UI cannot import product code. Validate aliases, relative imports, re-exports, and dynamic imports.
- Data Sync page entrypoints can be imported by `app/router`. Private `list/`, `editor/`, and `detail/` implementation paths cannot be deep-imported from other domains or each other. Share stable behavior through a deliberately owned module, not by opening arbitrary private imports.
- New architecture checks require positive and negative regression tests. Track genuine historical dependencies explicitly; do not silence failures through permissive ignore lists, deleting tests, or fabricated passing results.
- Report formatting, lint, typing, contracts, builds, and actual browser validation separately. A TypeScript build is not a substitute for interaction testing.

## Design System and Interaction

- Use Yak UI PageHeader, Table, Form/Field, Input, Select, Modal/Dialog, Alert, and Toast components. Tailwind and semantic tokens own product styling; source and types define shared component behavior.
- Default management forms use a compact horizontal label/control layout with a 104px label column and small outlined controls. Domains own field dependencies and submission; use Yak UI Field primitives for accessibility and validation.
- Select options use real domain values and labels. Consumers own search, refresh, and asynchronous candidates; shared controls own keyboard interaction and popup lifetime.
- Table owns generic selection, sorting, pagination, and sticky columns. Pages own queries, filters, row actions, and server-side pagination; do not duplicate Table internals.
- Modal/Dialog/Drawer use shared focus, Escape, closing, and scrolling behavior. Form dialogs normally require an explicit close action; destructive operations require confirmation. Domains own open and submission state.
- Use Alert for persistent risks or critical prerequisites, Toast for transient action results, and FieldError for field validation.
- Preserve keyboard navigation, focus visibility, accessible labels, and reduced-motion behavior. Animations must clean up promptly on interruption, hiding, and unmount.
- Shared component props and timing behavior are defined by their implementation and types, not by duplicated documentation. Extend controls only in response to verified needs.

## UI Copy Minimalism

Do not add persistent help, tutorial, explanatory, or repetitive product copy by default. Prefer labels, control types, placeholders, disabled/loading/empty states, field errors, and actual domain statuses.

Additional copy is justified only when missing information risks data loss or an irreversible action, a critical prerequisite would otherwise be unclear, or an operation is blocked for a reason not expressible by the control and its field error.

Place necessary copy near the relevant action and keep it short. Do not explain the same thing repeatedly through Label, Description, Helper Text, and Alert. Keep backend implementation details out of ordinary product forms. When the interaction itself conveys the meaning, add no copy.

## Quality and Verification

- Retain Oxfmt, Oxlint, TypeScript, architecture checks, and production builds. Tests must claim only the behavior they actually cover.
- Maintain real dependency, directory, HTTP, and Workspace boundaries. Fix incorrect implementation rather than loosening checks or adding broad lint disables.
- Run focused formatting, types, builds, and executable checks based on changed scope. Record actual browser, keyboard, responsive, and reduced-motion verification when relevant, distinguishing passed, skipped, and not-run checks.

## Documentation and Proposals

- Express routine page work, styling changes, component fixes, and bug fixes through code, tests, and focused pull requests. Do not create progress-only domain `*_RULES.md` files or duplicate architectural descriptions.
- Use the repository YOIP process for major subsystem changes, public product settings, monitoring contracts, or other significant public contracts. Routine internal component refactors need no YOIP.
- Update user documentation when externally observable behavior, compatibility, or deployment requirements change. Engineering commands belong in [Frontend Tooling](docs/tooling.md).
- Write rules and comments in the present tense, describing current responsibilities and invariants. Keep rationale and history in YOIPs or PR discussions. Accurately document published compatibility and safety restrictions.
