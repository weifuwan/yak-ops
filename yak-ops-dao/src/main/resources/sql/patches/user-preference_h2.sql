-- User-scoped preferences; safe for existing databases and fresh installations.
CREATE TABLE IF NOT EXISTS t_yak_ops_user_preference (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    scene VARCHAR(64) NOT NULL,
    item_key VARCHAR(128) NOT NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    use_count BIGINT NOT NULL DEFAULT 0,
    last_used_time TIMESTAMP(3) DEFAULT NULL,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pref_user_scene_item UNIQUE (user_id, scene, item_key)
);
CREATE INDEX IF NOT EXISTS idx_pref_user_scene_fav
    ON t_yak_ops_user_preference (user_id, scene, favorite, sort_order);
