# Modeling schema scripts

Add module-owned, additive SQL scripts here as modeling entities are migrated.

Use separate database-dialect folders (for example, `mysql` and `postgresql`). Every project-owned table must include `project_code`, and all workspace-local unique constraints must include it.

Do not copy the original `data_modal` baseline SQL or execute destructive table drops against an existing Yak Ops database. Register each incremental script with the Yak Ops schema-upgrade mechanism when the corresponding domain tables are introduced.
