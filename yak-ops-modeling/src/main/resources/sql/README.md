# Modeling schema scripts

## Complete schema

The modeling module has one complete, repeatable schema script for each supported database dialect. Each script defines the same 22 logical tables and 62 indexes:

- `modeling_mysql.sql`
- `modeling_postgresql.sql`
- `modeling_h2.sql`

The scripts are organized in two ordered sections:

1. **DDL** creates all modeling tables, indexes and table comments.
2. **DML** normalizes warehouse-layer enum values, initializes atomic-indicator `dw_type`, and seeds default data for projects that already exist in `t_yak_ops_project`:
   - Five conventional warehouse layers: ODS, DIM, DWS, DWD and ADS.
   - Five unit-standard directories: currency, object quantity, ratio, ranking and time.
   - One time-period root, nine time-period categories, and seven common time-period assets.

All default-data statements are project-scoped and use deterministic IDs with `NOT EXISTS` checks. Re-running a complete schema script does not duplicate those defaults. Projects created later retain the service-layer lazy-initialization fallback.

All 22 modeling tables use the `t_yak_ops_` prefix and a non-null `project_code`. The MyBatis-Plus tenant interceptor supplies the project code from the authenticated Yak Ops workspace and scopes reads, updates and deletes for the explicit DAO tenant allowlist.

The database column for dimension-field precision is `field_precision`; the Java/API property remains `precision`. The derived-indicator table stores warehouse ownership in `dw_type` and does not contain the legacy `dwh_id` column. Other modeling assets retain their own `dwh_id` fields where their current entities still use them.

The complete dialect scripts already include all modifier-scope indexes and the derived-indicator `dw_type` column. The six one-time patch scripts for modifier indexes and derived-indicator migration have therefore been retired. A clean rebuild must use only the matching complete schema script; do not apply the retired patches afterward. If existing modeling data must be preserved, do not run the reset scripts—design a separate data-preserving migration instead.

## Full modeling reset and rebuild

The reset scripts below remove the 22 logical modeling tables under both their current `t_yak_ops_` names and their former `t_cockpit_` names. The legacy names are included because `SchemaDao.ensureModelingSchema()` can automatically rename a remaining `t_cockpit_` table back to its current name during schema initialization.

**Destructive scope:** all modeling records for every project are deleted. Yak Ops base tables—including `t_yak_ops_project`, users, roles, permissions, workflow and datasource tables—are not dropped or modified by these reset scripts.

Available manual reset scripts:

- `modeling_drop_mysql.sql`
- `modeling_drop_postgresql.sql`
- `modeling_drop_h2.sql`

Back up the target database before resetting. For a MySQL database, set `DB_HOST`, `DB_PORT`, `DB_USER` and `DB_NAME`, then run the following from the repository root:

```bash
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p "$DB_NAME" < yak-ops-modeling/src/main/resources/sql/modeling_drop_mysql.sql
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p "$DB_NAME" < yak-ops-modeling/src/main/resources/sql/modeling_mysql.sql
```

For PostgreSQL or H2, use the matching `modeling_drop_<dialect>.sql` and `modeling_<dialect>.sql` scripts. Do not run `yak-ops-dao/src/main/resources/sql/yak-ops_mysql.sql` for a modeling-only reset: that is the full Yak Ops baseline schema and contains destructive DDL for base tables.

`YakOpsManager` invokes `SchemaDao.ensureModelingSchema()` after the Yak Ops base schema/version is available. That method executes the matching complete modeling schema script. It is safe for the complete script to run again after a manual rebuild; the reset scripts are manual-only and must never be wired into application startup.

After rebuilding, verify that:

1. All 22 modeling tables exist and each has `project_code`.
2. The derived-indicator table has `dw_type` and no `dwh_id`.
3. The modifier table has the four project-scoped indexes `idx_yak_indicator_modifier_5` through `idx_yak_indicator_modifier_8`.
4. Each existing project has the five default warehouse layers, the five unit-standard directories, and the seeded time-period hierarchy/assets.
5. Base tables and existing project/user/role data remain unchanged.

Example MySQL checks:

```sql
SELECT COUNT(*) AS modeling_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    't_yak_ops_business_type', 't_yak_ops_business_type_related_data_domain',
    't_yak_ops_dw_layer', 't_yak_ops_data_domain', 't_yak_ops_business_process',
    't_yak_ops_data_mart', 't_yak_ops_subject_domain', 't_yak_ops_dimension_table',
    't_yak_ops_dimension_table_fields', 't_yak_ops_standard_directory',
    't_yak_ops_field_standard', 't_yak_ops_code_standard', 't_yak_ops_code_detail',
    't_yak_ops_unit_standard', 't_yak_ops_dictionary_standard', 't_yak_ops_indicator_atom',
    't_yak_ops_indicator_derivative', 't_yak_ops_indicator_compose',
    't_yak_ops_indicator_modifier', 't_yak_ops_time_period_category',
    't_yak_ops_time_period', 't_yak_ops_version_management'
  );

SHOW COLUMNS FROM t_yak_ops_indicator_derivative LIKE 'dw_type';
SHOW COLUMNS FROM t_yak_ops_indicator_derivative LIKE 'dwh_id';
SHOW INDEX FROM t_yak_ops_indicator_modifier;
SELECT project_code, COUNT(*) AS layer_count
FROM t_yak_ops_dw_layer
GROUP BY project_code;
```

## Schema compatibility

`SchemaDao.ensureModelingSchema()` supports the current MySQL, PostgreSQL and H2 dialect scripts. For a normal upgrade where modeling data must be preserved, the DAO may rename a former `t_cockpit_` table in place when its corresponding `t_yak_ops_` table does not exist. If both names exist, initialization stops and requires explicit reconciliation; it never drops or merges either table automatically.

If a legacy modeling table does not contain `project_code`, initialization stops rather than exposing unscoped rows to every workspace. Back up and explicitly assign those rows to a project before enabling the migrated module.

When another modeling table is added, update all three dialect scripts, the DAO tenant allowlist, the schema preflight list, and all three reset scripts together.
