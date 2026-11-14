# Modeling schema scripts

The modeling schema is additive and repeatable. It creates all 22 warehouse-planning, dimension-metadata, data-standard and indicator tables. It never drops or recreates an existing table. The scripts also normalize legacy layer codes (`dim`, `dwd`, `ads`, `other`) to the current `DwTypeEnum` values and the historical model code `DIM(` to `DIM`, so existing rows remain readable after the enum correction.

Supported complete dialect scripts:

- `modeling_mysql.sql`
- `modeling_postgresql.sql`
- `modeling_h2.sql`

All modeling tables use the `t_yak_ops_` prefix and include a non-null `project_code`. The MyBatis-Plus tenant interceptor supplies this value from the authenticated Yak Ops workspace and scopes reads, updates and deletes for the explicit DAO tenant allowlist.

The schema scripts also initialize the five conventional warehouse layers (ODS, DIM, DWS, DWD and ADS) for existing projects that do not yet have an ODS layer. The system-created ODS row acts as the initialization marker, so later startup runs do not recreate individually removed layers while that marker remains. Projects created after application startup receive the same defaults lazily on their first warehouse-layer query.

Before applying the normalized schema, `SchemaDao.ensureModelingSchema()` checks for the former `t_cockpit_` table names. When only the legacy table exists, it renames that table in place and normalizes its index names, preserving existing rows. If both old and new table names exist, initialization stops and requires an explicit data reconciliation; it never drops or merges either table automatically.

If a legacy table does not contain `project_code`, initialization stops rather than exposing unscoped rows to every workspace. Back up and explicitly assign those rows to a project before enabling the migrated module.

The three scripts contain the same 22 logical tables and 54 indexes. When another modeling table is added, update all dialect scripts, the DAO tenant allowlist and the schema preflight list together.

## Manual reset scripts

The following scripts drop all 22 modeling tables, including data belonging to every Yak Ops project:

- `modeling_drop_mysql.sql`
- `modeling_drop_postgresql.sql`
- `modeling_drop_h2.sql`

These scripts are destructive and are intentionally separate from the additive schema scripts above. Run the matching script manually only when a full modeling reset is intended and after backing up the target database. Never add them to application startup or normal schema initialization.
