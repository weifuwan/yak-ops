-- One-time additive indexes for existing PostgreSQL installations.
-- IF NOT EXISTS makes this patch safe to re-run.
CREATE INDEX IF NOT EXISTS idx_yak_indicator_modifier_5 ON t_yak_ops_indicator_modifier (project_code, dwh_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_modifier_6 ON t_yak_ops_indicator_modifier (project_code, bus_type_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_modifier_7 ON t_yak_ops_indicator_modifier (project_code, domain_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_modifier_8 ON t_yak_ops_indicator_modifier (project_code, dimension_tb_id, dimension_fd_id);
