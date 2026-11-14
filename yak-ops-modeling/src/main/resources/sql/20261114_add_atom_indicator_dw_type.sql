-- One-time upgrade for existing databases.
-- Adds the warehouse-layer classification used by the atomic-indicator tree.
-- Compatible with MySQL, PostgreSQL and openGauss. Execute once per database.

ALTER TABLE t_yak_ops_indicator_atom
    ADD COLUMN dw_type varchar(32) DEFAULT 'PUBLIC_LAYER';

UPDATE t_yak_ops_indicator_atom
SET dw_type = 'PUBLIC_LAYER'
WHERE dw_type IS NULL;

CREATE INDEX idx_yak_indicator_atom_6
    ON t_yak_ops_indicator_atom (dw_type);
