# Yak Ops Improvement Proposals

YOIPs record **long-lived architectural decisions** for significant subsystems, public interfaces, checkpoint or persistent state formats, and compatibility. They are not per-PR implementation notes. Follow the [YOIP template](../../.github/YOIP_TEMPLATE.md) and [contribution process](../../.github/CONTRIBUTING.md).

## Proposal Records

| ID | Proposal | Recorded status |
| --- | --- | --- |
| YOIP-1 | [Checkpoint and End-of-Input Semantics](YOIP-1-checkpoint-and-end-of-input.md) | Accepted |
| YOIP-2 | [Task Execution Lifecycle and YakFlow Integration](YOIP-2-task-execution-lifecycle.md) | Accepted |
| YOIP-3 | [Connector Creation and Datasource Lookup](YOIP-3-connector-creation-and-datasource-lookup.md) | Accepted (decision link not recorded) |
| YOIP-4 | [Schedule Lifecycle and Quartz Integration](YOIP-4-schedule-lifecycle-quartz.md) | Accepted |
| YOIP-5 | [Data Integration Synchronization Model](YOIP-5-data-integration-sync-product-model.md) | Accepted; V3 parameter shape superseded by YOIP-20 |
| YOIP-6 | [MySQL Chunk Backfill and Durable Snapshot Output](YOIP-6-mysql-chunk-backfill-checkpoints.md) | Discussion (implementation merged) |
| YOIP-20 | [Data Sync V4 Component-Owned Configuration Contract](YOIP-20-data-sync-v4-configuration-contract.md) | Accepted |
| YOIP-21 | [Data Integration Workflows Aligned with DataWorks](YOIP-21-dataworks-data-integration-workflows.md) | Discussion |

YOIP-3 retains its original Accepted label, but no independent decision permalink was recorded. YOIP-6 is still Discussion even though its implementation PR was merged. YOIP-20 supersedes the V3 persisted parameter shape described in YOIP-5, not the entire product model. Neither implementation merges nor passing CI substitute for a design decision.

## New Proposals

A YOIP is one significant architectural or public-contract decision, **not** an implementation step or test report. Follow the [template](../../.github/YOIP_TEMPLATE.md) and [contribution process](../../.github/CONTRIBUTING.md). Check open proposal PRs and choose a new, unused ID (22 or later; IDs 7–19 are retired and reserved). Add the document and update this index in the same PR.

Start with `Discussion`, then link that proposal PR's real URL. Mark `Accepted` only after an explicit maintainer `[VOTE]` and a same-PR decision-comment permalink. The PR-scope validator checks document structure and link shape; only human review establishes a real decision.

Retired implementation notes and the former duplicate CDC YOIP-4 navigation page have been removed rather than retained as a parallel document archive. Git history and the original implementation PRs remain the historical record; the accepted Quartz scheduling proposal retains YOIP-4.
