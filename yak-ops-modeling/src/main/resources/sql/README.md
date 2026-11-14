# Modeling schema scripts

The modeling schema is additive and repeatable. Each database-specific script is organized in two ordered sections:

1. **DDL** creates the complete set of 22 warehouse-planning, dimension-metadata, data-standard and indicator tables, indexes and table comments.
2. **DML** runs after all DDL and performs legacy-value normalization, atomic-indicator `dw_type` initialization, and project-scoped default-data seeding:
   - Five conventional warehouse layers: ODS, DIM, DWS, DWD and ADS.
   - Five unit-standard directories: currency, object quantity, ratio, ranking and time.
   - One time-period root, nine time-period categories, and seven common time-period assets.

Supported complete dialect scripts:

- `modeling_mysql.sql`
- `modeling_postgresql.sql`
- `modeling_h2.sql`

The standalone `20261114_add_atom_indicator_dw_type.sql` patch is no longer needed: `dw_type`, its index and the default-value normalization are maintained in each complete dialect script. The dimension-field precision column is named `field_precision` in the database to avoid the problematic identifier `precision`; the Java/API property remains `precision` to preserve the existing request/response contract.

All modeling tables use the `t_yak_ops_` prefix and include a non-null `project_code`. The MyBatis-Plus tenant interceptor supplies this value from the authenticated Yak Ops workspace and scopes reads, updates and deletes for the explicit DAO tenant allowlist.

All default-data statements read existing projects from `t_yak_ops_project`; they do not create projects. They use project-scoped `NOT EXISTS` checks and deterministic IDs so repeated schema initialization does not duplicate the default rows. The system-created ODS row remains the initialization marker for the five warehouse layers. The service layer retains lazy initialization as a fallback for projects created after application startup.

Before applying the normalized schema, `SchemaDao.ensureModelingSchema()` checks for the former `t_cockpit_` table names. When only the legacy table exists, it renames that table in place and normalizes its index names, preserving existing rows. If both old and new table names exist, initialization stops and requires an explicit data reconciliation; it never drops or merges either table automatically.

If a legacy table does not contain `project_code`, initialization stops rather than exposing unscoped rows to every workspace. Back up and explicitly assign those rows to a project before enabling the migrated module.

The three scripts contain the same 22 logical tables and 55 indexes. When another modeling table is added, update all dialect scripts, the DAO tenant allowlist and the schema preflight list together.

## Manual reset scripts

The following scripts drop all 22 modeling tables, including data belonging to every Yak Ops project:

- `modeling_drop_mysql.sql`
- `modeling_drop_postgresql.sql`
- `modeling_drop_h2.sql`

These scripts are destructive and are intentionally separate from the additive schema scripts above. Run the matching script manually only when a full modeling reset is intended and after backing up the target database. Never add them to application startup or normal schema initialization.
