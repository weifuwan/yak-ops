package io.yak.ops.business.datasource.management;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;

/**
 * 承载数据源新增或编辑时已经解析完成的业务配置。
 *
 * @param name 数据源名称
 * @param dbType 数据库类型
 * @param environment 运行环境
 * @param remark 备注
 * @param connectionJson 动态表单提交的连接参数 JSON
 * @author weifuwan
 * @since 2026-09-24
 */
public record DataSourceConfigurationCommand(
        String name,
        DataSourceDbType dbType,
        DataSourceEnvironment environment,
        String remark,
        String connectionJson) {}
