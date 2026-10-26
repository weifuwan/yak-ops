# Yak Ops Improvement Proposals

YOIPs record **long-lived architectural decisions** for significant subsystems, public interfaces, checkpoint or persistent state formats, and compatibility. They are not per-PR implementation notes. Follow the [YOIP template](../../.github/YOIP_TEMPLATE.md) and [contribution process](../../.github/CONTRIBUTING.md).

## Proposal Records

| ID | Proposal | Recorded status |
| --- | --- | --- |
| YOIP-1 | [Checkpoint and End-of-Input Semantics](YOIP-1-checkpoint-and-end-of-input.md) | Accepted |
| YOIP-2 | [Task Execution Lifecycle and YakFlow Integration](YOIP-2-task-execution-lifecycle.md) | Accepted |
| YOIP-3 | [Connector Creation and Datasource Lookup](YOIP-3-connector-creation-and-datasource-lookup.md) | Accepted (decision link not recorded) |
| YOIP-4 | [Schedule Lifecycle and Quartz Integration](YOIP-4-schedule-lifecycle-quartz.md) | Accepted |
| YOIP-5 | [Data Integration Synchronization Model](YOIP-5-data-integration-sync-product-model.md) | Accepted |
| YOIP-6 | [MySQL Chunk Backfill and Durable Snapshot Output](YOIP-6-mysql-chunk-backfill-checkpoints.md) | Discussion (implementation merged) |
| YOIP-20 | [Data Sync V4 Component-Owned Configuration Contract](YOIP-20-data-sync-v4-configuration-contract.md) | Accepted |

YOIP-3 retains the original document's Accepted label, but no independent decision permalink was included in that historical record. YOIP-6 remains Discussion even though its implementation PR was merged. Merging implementation code or passing CI does not substitute for a design decision.

## New Proposals

A YOIP is one significant architectural or public-contract decision, **not** an implementation step or test report. Follow the [template](../../.github/YOIP_TEMPLATE.md) and [contribution process](../../.github/CONTRIBUTING.md). Check open proposal PRs and choose a new, unused ID beyond the retired 7–19 range. Add the document and update this index in the same PR.

Start with `Discussion`, then link that proposal PR's real URL. Mark `Accepted` only after an explicit maintainer `[VOTE]` and a same-PR decision-comment permalink. The PR-scope validator checks document structure and link shape; only human review establishes a real decision.

## Historical Engineering Notes

The original repository contained two documents labeled YOIP-4 and numbered individual CDC implementation notes as YOIP-7 through YOIP-19. These are **not separate accepted proposals**. Their former paths remain as navigation notices so links from earlier PRs remain usable. The full text is retained under [`docs/engineering/cdc/`](../engineering/cdc/README.md). The former identifiers are reserved to avoid confusing old references with future designs.

| Former label | Preserved engineering note | Change history |
| --- | --- | --- |
| duplicate YOIP-4 | [CDC Source Boundaries](../engineering/cdc/cdc-source-boundaries.md) | [source](https://github.com/weifuwan/yak-ops/commit/9b7c73e3f93977c7bc3f02353c1ecc623e5ee726) |
| former YOIP-7 | [MySQL CDC Keyless Snapshot Reliability Baseline](../engineering/cdc/mysql-cdc-keyless-snapshot-baseline.md) | [source](https://github.com/weifuwan/yak-ops/commit/fa8b0ed242d5b9b2189222cb24a167ae1d9b7da3) |
| former YOIP-8 | [MySQL CDC Typed Snapshot Checkpoint Compatibility](../engineering/cdc/mysql-typed-checkpoint-recovery.md) | [source](https://github.com/weifuwan/yak-ops/commit/2601ff597ecd8adc9131fce4052f09b185afb447) |
| former YOIP-9 | [CDC Chunk Key and JDBC Row Identity Acceptance](../engineering/cdc/cdc-row-identity-acceptance.md) | [source](https://github.com/weifuwan/yak-ops/commit/0e30a3079c33ca632bf1d3a4bd3f926dd560f008) |
| former YOIP-10 | [Keyless CDC Admission Contract](../engineering/cdc/keyless-cdc-admission-contract.md) | [source](https://github.com/weifuwan/yak-ops/pull/1726) |
| former YOIP-11 | [Typed Snapshot Split Assignment and Recovery](../engineering/cdc/keyless-typed-chunk-assignment.md) | [source](https://github.com/weifuwan/yak-ops/pull/1727) |
| former YOIP-12 | [Typed Snapshot JDBC Reader and Recovery Boundary](../engineering/cdc/keyless-typed-snapshot-reader.md) | [source](https://github.com/weifuwan/yak-ops/pull/1728) |
| former YOIP-13 | [Keyless Snapshot Backfill Integration](../engineering/cdc/keyless-snapshot-backfill-integration.md) | [source](https://github.com/weifuwan/yak-ops/pull/1729) |
| former YOIP-14 | [Keyless Binlog Handoff and Consistent Snapshot Boundaries](../engineering/cdc/keyless-binlog-handoff.md) | [source](https://github.com/weifuwan/yak-ops/pull/1730) |
| former YOIP-15 | [CDC Change Identity and Runtime Delivery](../engineering/cdc/cdc-change-identity-delivery.md) | [source](https://github.com/weifuwan/yak-ops/pull/1731) |
| former YOIP-16 | [MySQL JDBC Exact-Row RETRACT](../engineering/cdc/mysql-jdbc-exact-row-retract.md) | [source](https://github.com/weifuwan/yak-ops/pull/1732) |
| former YOIP-18 | [PostgreSQL and Oracle Keyless Target Acceptance](../engineering/cdc/postgres-oracle-keyless-target-acceptance.md) | [source](https://github.com/weifuwan/yak-ops/pull/1734) |
| former YOIP-19 | [Keyless Repeated-Row Snapshot Checkpoint Compaction](../engineering/cdc/keyless-large-duplicate-groups.md) | [source](https://github.com/weifuwan/yak-ops/pull/1735) |

This consolidation has no impact on product behavior, accepted public contracts, or the status of an outstanding design decision.
