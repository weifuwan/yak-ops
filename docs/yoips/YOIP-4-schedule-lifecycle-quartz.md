# YOIP-4: Schedule Lifecycle + Quartz Integration

Status: Discussion

Discussion: Pending proposal PR link

Decision: Pending

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

Introduce independent time scheduling for eligible published Tasks through the existing YOIP-2 submission and admission path. Quartz supplies time triggers; Task plugins execute Tasks. Workflow dependency progression remains outside this proposal's implementation scope.

## Public Interfaces

Propose scheduler API and Quartz implementation modules, Workspace-scoped schedule CRUD and preview APIs, nullable scheduled-Instance provenance, and a default-false periodic-execution capability on the existing execution plugin contract.

## Proposed Changes

Use single-owner Quartz JDBC JobStore. Separate desired schedule state from registration health. Define stable occurrence identity, final fire outcomes, atomic schedule guards and Task admission, no catch-up of unadmitted downtime occurrences, and reuse of existing Cron frontend components.

## Compatibility, Deprecation, and Migration Plan

This discussion PR changes documentation only. Preserve manual submission, Task Instance lifecycle, checkpoint formats, and published migration history. Implementation and acceptance require a separate explicit maintainer decision.

## Test Plan

Require duplicate-fire, skip-replay, stop/admission race, revision, authorization, registration reconciliation, restart, UTC/DST, retention, frontend, and real scheduled JDBC acceptance coverage. These are future test requirements, not executed results.

## Rejected Alternatives

Do not put scheduling into DATA_SYNC, execute Task jobs directly from Quartz, treat Quartz concurrency controls as business admission, create a second execution lifecycle, or implement Workflow DAG scheduling in this proposal.
