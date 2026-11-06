-- Apply manually to existing MySQL installations after assigning every datasource to a valid Project.
-- Never run the baseline SQL on an existing database; it contains DROP TABLE statements.
-- Step 1 (expand):
ALTER TABLE t_yak_ops_datasource ADD COLUMN project_code BIGINT NULL;
-- Step 2 (backfill): inspect all records and fill project_code according to business ownership.
-- UPDATE t_yak_ops_datasource SET project_code = <chosen_project_code> WHERE id = <existing_datasource_id>;
-- Mandatory preflight: this query must return zero rows before Step 3.
SELECT d.id, d.name, d.project_code
FROM t_yak_ops_datasource d
LEFT JOIN t_yak_ops_project p ON d.project_code = p.code
WHERE p.code IS NULL;
-- Step 3 (contract): run only when every datasource is assigned and existing references audited.
-- ALTER TABLE t_yak_ops_datasource DROP INDEX t_yak_ops_datasource_name_un;
-- ALTER TABLE t_yak_ops_datasource MODIFY project_code BIGINT NOT NULL;
-- ALTER TABLE t_yak_ops_datasource ADD UNIQUE KEY uk_datasource_project_name (project_code, name);
-- Do not uncomment Step 3 before validating orphan counts and duplicate (project_code, name).
