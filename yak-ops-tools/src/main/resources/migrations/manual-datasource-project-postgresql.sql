-- Manual migration for existing PostgreSQL installations.
-- Run during maintenance after auditing existing datasource ID references.
ALTER TABLE t_yak_ops_datasource ADD COLUMN project_code BIGINT;
-- Backfill each legacy datasource with the owning Project's code:
-- UPDATE t_yak_ops_datasource SET project_code = <project_code> WHERE id = <datasource_id>;
-- Verify zero orphaned/unassigned rows:
SELECT d.id, d.name, d.project_code
FROM t_yak_ops_datasource d
LEFT JOIN t_yak_ops_project p ON p.code = d.project_code
WHERE p.code IS NULL;
-- Only after backfill (uncomment):
-- ALTER TABLE t_yak_ops_datasource ALTER COLUMN project_code SET NOT NULL;
-- ALTER TABLE t_yak_ops_datasource DROP CONSTRAINT t_yak_ops_datasource_name_un;
-- ALTER TABLE t_yak_ops_datasource ADD CONSTRAINT uk_datasource_project_name UNIQUE (project_code, name);
