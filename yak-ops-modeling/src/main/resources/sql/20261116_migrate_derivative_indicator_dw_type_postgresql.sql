-- Existing PostgreSQL installations only. Back up the table before applying.
ALTER TABLE t_yak_ops_indicator_derivative
  ADD COLUMN IF NOT EXISTS dw_type varchar(32);

UPDATE t_yak_ops_indicator_derivative d
SET dw_type = (SELECT l.dw_type FROM t_yak_ops_dw_layer l WHERE l.id = d.dwh_id)
WHERE d.dw_type IS NULL
  AND EXISTS (SELECT 1 FROM t_yak_ops_dw_layer l WHERE l.id = d.dwh_id);

UPDATE t_yak_ops_indicator_derivative
SET dw_type = 'APPLICATION_LAYER'
WHERE dw_type IS NULL
  AND (mart_id IS NOT NULL OR subject_id IS NOT NULL)
  AND bus_type_id IS NULL AND domain_id IS NULL AND bus_process_id IS NULL;

UPDATE t_yak_ops_indicator_derivative
SET dw_type = 'PUBLIC_LAYER'
WHERE dw_type IS NULL
  AND (bus_type_id IS NOT NULL OR domain_id IS NOT NULL OR bus_process_id IS NOT NULL)
  AND mart_id IS NULL AND subject_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_yak_indicator_derivative_6 ON t_yak_ops_indicator_derivative (dw_type);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_derivative_7 ON t_yak_ops_indicator_derivative (mart_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_derivative_8 ON t_yak_ops_indicator_derivative (subject_id);

-- Review unsupported, conflicting, or otherwise unmapped legacy records before dropping dwh_id.
SELECT id, dwh_id, dw_type, mart_id, subject_id, bus_type_id, domain_id, bus_process_id
FROM t_yak_ops_indicator_derivative
WHERE dw_type IS NULL OR dw_type NOT IN ('PUBLIC_LAYER', 'APPLICATION_LAYER')
   OR ((mart_id IS NOT NULL OR subject_id IS NOT NULL)
       AND (bus_type_id IS NOT NULL OR domain_id IS NOT NULL OR bus_process_id IS NOT NULL));
