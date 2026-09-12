ALTER TABLE yak_offline_sync_cursor
    ADD COLUMN source_signature CHAR(64) NULL
        COMMENT '游标绑定的数据源、表和字段摘要' AFTER source_column;
