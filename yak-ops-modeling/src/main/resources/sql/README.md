# Modeling schema scripts

These scripts are additive and repeatable. They create the warehouse-planning tables and project-code indexes only; they never drop or recreate tables.

Supported dialect scripts:

- `modeling_mysql.sql`
- `modeling_postgresql.sql`
- `modeling_h2.sql`

Every migrated project-owned modeling table includes a non-null `project_code`. The MyBatis-Plus tenant interceptor supplies this value from the authenticated Yak Ops workspace and scopes reads, updates and deletes for the explicit DAO tenant allowlist.

If a table already exists without `project_code`, the initializer refuses to reuse it. Do not add a default project code automatically: existing legacy rows need an explicit workspace assignment decision first.

When another modeling table is migrated, update the SQL scripts, DAO tenant allowlist and schema preflight list together.
