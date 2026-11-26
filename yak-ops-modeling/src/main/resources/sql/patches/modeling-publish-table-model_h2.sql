-- Add the model type to existing Doris publish-history tables.
-- Safe to execute repeatedly; fresh installations already include this column.
ALTER TABLE IF EXISTS t_yak_ops_dimension_table_publish
    ADD COLUMN IF NOT EXISTS table_model VARCHAR(32);
