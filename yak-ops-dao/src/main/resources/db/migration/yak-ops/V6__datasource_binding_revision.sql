ALTER TABLE yak_ops_data_source
  ADD COLUMN revision BIGINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT 'Monotonic datasource binding revision for verified background execution';
