# Frontend Rules

Scope:
- `yak-ops-ui/src/**`

Depends On:
- `./ARCHITECTURE.md`
- behavior changes load `./TEST_RULES.md`
- backend calls load `./SERVICE_RULES.md`

Principles:
- ownership first
- one source of truth
- no speculative abstraction
- Datasource-only product scope

## Must

- new product behavior belongs to Datasource unless scope explicitly changes.
- page, hook and component state stays at the smallest real owner.
- backend business facts come from the backend, not fabricated browser state.
- values derivable from props/state are computed directly instead of copied into synchronized state.
- `useMemo` / `useCallback` are used only when real cost or reference stability requires them.
- Effects handle external synchronization, not ordinary derived state.
- Hook dependencies stay complete.
- React components are declared at module scope.
- public reusable exports use clear named exports where practical.
- interactive elements preserve keyboard/focus/disabled semantics.
- TypeScript errors and lint findings are fixed at the source, not hidden with broad disables.
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
- rewrite historical frontend architecture as a side effect of one feature.

## Validation

Local checks should match the change:

```bash
cd yak-ops-ui
yarn lint
yarn test
yarn build
```

Current CI runs `yarn build` only; test/lint evidence must be stated separately until CI is upgraded.
