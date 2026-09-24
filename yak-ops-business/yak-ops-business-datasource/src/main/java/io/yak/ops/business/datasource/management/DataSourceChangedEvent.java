package io.yak.ops.business.datasource.management;

/**
 * 数据源配置提交成功后用于失效本地 Catalog 元数据缓存。
 *
 * @param dataSourceId 已发生变化的数据源 ID
 * @author weifuwan
 * @since 2026-09-24
 */
public record DataSourceChangedEvent(Long dataSourceId) {}
