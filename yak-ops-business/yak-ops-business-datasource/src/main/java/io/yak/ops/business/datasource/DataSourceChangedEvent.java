package io.yak.ops.business.datasource;

/**
 * 数据源配置提交成功后发布的变更事件，用于失效依赖数据源配置的本地状态。
 *
 * @param dataSourceId 已发生变化的数据源 ID
 * @author weifuwan
 * @since 2026-09-24
 */
public record DataSourceChangedEvent(String dataSourceId) {}
