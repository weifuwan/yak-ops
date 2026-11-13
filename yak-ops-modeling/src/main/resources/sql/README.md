# Modeling schema scripts

Add module-owned, additive SQL scripts here as modeling entities are migrated.

Use separate database-dialect folders (for example, `mysql` and `postgresql`). Every project-owned table must include `project_code`, and all workspace-local unique constraints must include it.

Do not copy the original `data_modal` baseline SQL or execute destructive table drops against an existing Yak Ops database. When the first modeling tables are introduced, extend `yak-ops-tools` to execute these module-owned incremental scripts idempotently after the existing Yak Ops schema has been validated.
