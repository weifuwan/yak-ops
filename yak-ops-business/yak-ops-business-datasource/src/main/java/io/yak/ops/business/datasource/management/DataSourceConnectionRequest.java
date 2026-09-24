package io.yak.ops.business.datasource.management;

import io.yak.ops.common.enums.datasource.DataSourceDbType;

/**
 * 承载未保存或编辑态的数据源连接测试参数。
 *
 * @param dataSourceId 已保存数据源 ID；新增场景为空
 * @param requestedType 请求指定的数据源类型；为空时从连接 JSON 解析
 * @param connectionJson 动态表单连接参数 JSON
 * @author weifuwan
 * @since 2026-09-24
 */
public record DataSourceConnectionRequest(
        Long dataSourceId, DataSourceDbType requestedType, String connectionJson) {}
