# Retired Data Sync Product Contract

The previous Data Sync product workflow and its backend Task plugin are no longer available in the current source tree. This document formerly described the legacy single-table batch and database-replication configuration, including product-specific JSON parameter indexes and operations reporting; those descriptions are no longer valid operational guidance.

The shared Datasource, Task Definition, Task Instance, and scheduling APIs remain, while Flink CDC is developed as a separate engine.

The `/api/v1/data-sync/operations/dashboard` endpoint was retired. Task Definition pagination no longer supports the product-specific `parameterFilters`; requests with nonempty legacy filters are explicitly rejected instead of silently returning unfiltered data. The frontend was not changed during this backend cleanup, so its old synchronization and operations views are not functional until a new backend integration is implemented.

Historical details remain available in the repository's Git history and original PR discussions. Do not use the old JSON examples or test expectations for a new CDC Pipeline.
