# Modeling schema scripts

The modeling schema is additive and repeatable. It creates all 22 warehouse-planning, dimension-metadata, data-standard and indicator tables. It never drops or recreates an existing table.

Supported complete dialect scripts:

- `modeling_mysql.sql`
- `modeling_postgresql.sql`
- `modeling_h2.sql`

All modeling tables use the `t_yak_ops_` prefix and include a non-null `project_code`. The MyBatis-Plus tenant interceptor supplies this value from the authenticated Yak Ops workspace and scopes reads, updates and deletes for the explicit DAO tenant allowlist.

Before applying the normalized schema, `SchemaDao.ensureModelingSchema()` checks for the former `t_cockpit_` table names. When only the legacy table exists, it renames that table in place and normalizes its index names, preserving existing rows. If both old and new table names exist, initialization stops and requires an explicit data reconciliation; it never drops or merges either table automatically.

If a legacy table does not contain `project_code`, initialization stops rather than exposing unscoped rows to every workspace. Back up and explicitly assign those rows to a project before enabling the migrated module.

The three scripts contain the same 22 logical tables and 54 indexes. When another modeling table is added, update all dialect scripts, the DAO tenant allowlist and the schema preflight list together.
