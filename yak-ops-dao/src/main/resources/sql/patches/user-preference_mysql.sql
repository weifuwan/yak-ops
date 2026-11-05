-- User-scoped preferences; safe for existing databases and fresh installations.
CREATE TABLE IF NOT EXISTS `t_yak_ops_user_preference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `scene` VARCHAR(64) NOT NULL,
    `item_key` VARCHAR(128) NOT NULL,
    `favorite` TINYINT(1) NOT NULL DEFAULT 0,
    `sort_order` INT NOT NULL DEFAULT 0,
    `use_count` BIGINT NOT NULL DEFAULT 0,
    `last_used_time` DATETIME(3) DEFAULT NULL,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pref_user_scene_item` (`user_id`, `scene`, `item_key`),
    KEY `idx_pref_user_scene_fav` (`user_id`, `scene`, `favorite`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
