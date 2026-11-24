# Modeling schema scripts

## Complete schema

The modeling module has one complete, repeatable schema script for each supported database dialect. Each script defines the same 23 logical tables with aligned table and index definitions:

- `modeling_mysql.sql`
- `modeling_postgresql.sql`
- `modeling_h2.sql`

The scripts are organized in two ordered sections:

1. **DDL** creates all modeling tables, indexes and table comments.
2. **DML** normalizes warehouse-layer enum values, initializes atomic-indicator `dw_type`, and seeds default data for projects that already exist in `t_yak_ops_project`:
   - Five conventional warehouse layers: ODS, DIM, DWS, DWD and ADS.
   - One protected system-default data domain (`default`) and its default business process per existing project.
   - Five unit-standard directories: currency, object quantity, ratio, ranking and time.
   - One time-period root, nine time-period categories, and seven common time-period assets.

All default-data statements are project-scoped and use deterministic IDs with `NOT EXISTS` checks. Re-running a complete schema script does not duplicate those defaults or overwrite an existing data domain. The default data domain is marked `flag = 1`, which protects it from deletion and locks its identifying names. The matching default business process is also marked `flag = 1`, consistent with normal data-domain creation. Data domains for projects created after startup are seeded on the next schema initialization; the warehouse layers also support their existing service-layer lazy initialization.

All 23 current modeling tables use the `t_yak_ops_` prefix and a non-null `project_code`. The MyBatis-Plus tenant interceptor supplies the project code from the authenticated Yak Ops workspace and scopes reads, updates and deletes for the explicit DAO tenant allowlist.

The dimension-model table includes `en_short_name` for DIM assets. `SchemaDao.ensureModelingSchema()` additively creates this column on existing installations and backfills it from legacy DIM `table_name` values without deleting the legacy value. New DIM records use `en_short_name`; other model types continue to use `table_name`. Dimension-field metadata now includes `ordinal`, `field_scale`, `datetime_precision` and `default_value`; the existing `field_precision` column remains the DECIMAL precision property. Schema initialization adds the new columns to existing installations without dropping field data. Dimension version snapshots use schema version 4 and include only table metadata and ordered logical field definitions. Historical version envelopes that also contain Doris configuration are still readable; rollback restores the logical model only and never restores publish settings.

Doris physical settings are resolved at preview/publish time and stored as an immutable `physical_config` snapshot on `t_yak_ops_dimension_table_publish` alongside the generated DDL, model version, datasource target and execution result. The logical field `primary_flag` is the only source of key semantics: no primary fields produces `DUPLICATE KEY`; one or more primary fields produces `UNIQUE KEY`. HASH distribution is fixed, automatic bucket sizing is the default, and optional fixed buckets / AUTO RANGE time partitioning are advanced publish parameters. `AGGREGATE KEY` is intentionally deferred. DDL preview and publishing reuse the same `DorisTypeMapper` and `DorisSchemaFactory` used by the Yak-OPS Flink CDC Doris connector. The logical `DIM` business-dimension asset cannot be published as a physical table. The three former mutable Doris configuration tables are retired; startup migration transfers their Key fields into `primary_flag` before dropping them.

The database column for dimension-field precision is `field_precision`; the Java/API property is `fieldPrecision`. All four indicator-asset tables (modifier, atomic, derivative and composite) store warehouse classification in `dw_type`; they do not reference a concrete warehouse-layer row. Only the dimension-model table retains `dwh_id`, because a dimension table must reference its actual warehouse-layer definition.

The complete dialect scripts already include all modifier-scope indexes and the `dw_type` columns and indexes for indicator assets. The six one-time patch scripts for modifier indexes and derived-indicator migration have therefore been retired. A clean rebuild must use only the matching complete schema script; do not apply the retired patches afterward. If existing modeling data must be preserved, do not run the reset scripts—design a separate data-preserving migration instead.

## Full modeling reset and rebuild

The reset scripts below remove the 23 current logical modeling tables and also drop the three retired Doris configuration tables when present under both their current `t_yak_ops_` names and their former `t_cockpit_` names. The legacy names are included so reset can also clean up installations that still have former `t_cockpit_` tables. The 23 current logical tables are renamed in place when appropriate; the three retired Doris config tables are migrated and dropped by the dedicated Doris schema migration.

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

1. All 23 current modeling tables exist and each has `project_code`; retired Doris config tables are absent.
2. The modifier, atomic, derivative and composite indicator tables have `dw_type` and no `dwh_id`; the dimension-model table retains `dwh_id`.
3. The modifier table has the four project-scoped indexes `idx_yak_indicator_modifier_5` through `idx_yak_indicator_modifier_8`.
4. Each existing project has the five default warehouse layers, one system-default data domain with its default business process, the five unit-standard directories, and the seeded time-period hierarchy/assets.
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
    't_yak_ops_dimension_table_fields', 't_yak_ops_dimension_table_publish', 't_yak_ops_standard_directory',
    't_yak_ops_field_standard', 't_yak_ops_code_standard', 't_yak_ops_code_detail',
    't_yak_ops_unit_standard', 't_yak_ops_dictionary_standard', 't_yak_ops_indicator_atom',
    't_yak_ops_indicator_derivative', 't_yak_ops_indicator_compose',
    't_yak_ops_indicator_modifier', 't_yak_ops_time_period_category',
    't_yak_ops_time_period', 't_yak_ops_version_management'
  );

SHOW COLUMNS FROM t_yak_ops_indicator_modifier LIKE 'dw_type';
SHOW COLUMNS FROM t_yak_ops_indicator_compose LIKE 'dw_type';
SHOW COLUMNS FROM t_yak_ops_indicator_atom LIKE 'dw_type';
SHOW COLUMNS FROM t_yak_ops_indicator_derivative LIKE 'dw_type';
SHOW COLUMNS FROM t_yak_ops_dimension_table LIKE 'dwh_id';
SHOW COLUMNS FROM t_yak_ops_dimension_table_fields LIKE 'ordinal';
SHOW COLUMNS FROM t_yak_ops_dimension_table_fields LIKE 'field_scale';
SHOW COLUMNS FROM t_yak_ops_dimension_table_fields LIKE 'datetime_precision';
SHOW COLUMNS FROM t_yak_ops_dimension_table_publish LIKE 'physical_config';
SHOW COLUMNS FROM t_yak_ops_dimension_table_fields LIKE 'default_value';
SHOW INDEX FROM t_yak_ops_dimension_table_publish;
SHOW INDEX FROM t_yak_ops_indicator_modifier;
SELECT project_code, COUNT(*) AS layer_count
FROM t_yak_ops_dw_layer
GROUP BY project_code;

SELECT project_code, id, en_short_name, cn_name, flag
FROM t_yak_ops_data_domain
WHERE en_short_name = 'default';
```

## Schema compatibility

`SchemaDao.ensureModelingSchema()` supports the current MySQL, PostgreSQL and H2 dialect scripts. For a normal upgrade where modeling data must be preserved, the DAO may rename a former `t_cockpit_` table in place when its corresponding `t_yak_ops_` table does not exist. If both names exist, initialization stops and requires explicit reconciliation; it never drops or merges either table automatically.

If a legacy modeling table does not contain `project_code`, initialization stops rather than exposing unscoped rows to every workspace. Back up and explicitly assign those rows to a project before enabling the migrated module.

When another modeling table is added, update all three dialect scripts, the DAO tenant allowlist, the schema preflight list, and all three reset scripts together.
