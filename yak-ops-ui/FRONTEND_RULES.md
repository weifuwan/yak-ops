# Frontend Rules

Scope:
- `yak-ops-ui/src/**`

Depends On:
- `./ARCHITECTURE.md`
- backend calls load `./SERVICE_RULES.md`

Principles:
- ownership first
- one source of truth
- no speculative abstraction
- Datasource-only product scope

## Must

- `src/pages` only contains `login` and `data-source`.
- new product behavior belongs to Datasource unless scope explicitly changes.
- page, hook and component state stays at the smallest real owner.
- backend business facts come from backend responses.
- values derivable from props/state are computed directly.
- `useMemo` / `useCallback` are used only when real cost or reference stability requires them.
- Effects handle external synchronization, not ordinary derived state.
- Hook dependencies stay complete.
- React components are declared at module scope.
- interactive elements preserve keyboard/focus/disabled semantics.
- TypeScript errors and lint findings are fixed at the source.
- existing Datasource components/utilities are reused before creating a second version.

## Must Not

- expose removed modules in navigation.
- call backend endpoints from random components when a Datasource Service owner exists.
- create Java-style interface / impl / adapter layers for simple frontend calls.
- add global state for page-local behavior.
- use `useEffect + setState` for values that can be derived.
- default-wrap ordinary calculations in `useMemo`.
- default-wrap ordinary event handlers in `useCallback`.
- add generic shared components that only one Datasource screen uses.
- recreate deleted Jest tests or CI as a side effect.

## Verification

Use only checks actually needed by the task, for example:

```bash
cd yak-ops-ui
npm run check
npm run build
```

Record what was actually executed.
