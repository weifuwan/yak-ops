# Realtime Sync Change

> This template is for changes touching `yak-ops-business-sync-realtime` or `yak-ops-ui/src/pages/realtime-sync`.
> Read `yak-ops-business/yak-ops-business-sync/yak-ops-business-sync-realtime/DOMAIN.md` first.

## Summary

<!-- What user/product problem does this PR solve? -->

## Domain Impact Analysis

```text
Bounded context: Realtime Sync / adjacent context
Aggregate(s): RealtimeSyncTask / DefinitionVersion / SyncExecution / none
SyncDefinition area: Endpoint / Route / Selector / Target / ReplayKey / SyncPolicy / ExecutionPolicy / none
Invariant/lifecycle impact:
Layer: Domain / Application / Infrastructure / Interface
Current mapping/gap:
Safety properties to preserve:
Domain Gap: no
```

If `Domain Gap: yes`, explain the approved domain-model extension before implementation.

## Behavior / compatibility

<!-- Explain DB/API/YAML/UI compatibility. Do not call legacy DraftRevision fields DefinitionVersion IDs. -->

## Safety checklist

- [ ] No second editable Spec/Definition truth was introduced.
- [ ] Start/Restart/Apply do not read mutable Draft incorrectly.
- [ ] UNKNOWN/CONFLICT cannot create a second active Execution.
- [ ] Idempotency / DB command serialization / stop-during-start are preserved when relevant.
- [ ] RuntimeEnvironmentSnapshot / runtime identity / credential zeroization are preserved when relevant.
- [ ] Core Domain remains free from Flink/SSH/JDBC/Spring/persistence concerns.
- [ ] No Big-Bang schema/API rename/drop was introduced.

## Tests / guardrails

```text
Static domain guard:
Core domain smoke:
Targeted JUnit tests:
Other validation:
```

## Domain Compliance Report

```text
Domain rule implemented:
Aggregate(s) affected:
Invariant/lifecycle changes:
Legacy compatibility retained:
Safety properties preserved:
DB/API migration mode:
Tests/guardrails added or updated:
Known gaps remaining:
```
