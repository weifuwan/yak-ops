package io.yak.ops.dao.repository.datasource;

import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;

/**
 * 数据源 Repository 分页筛选条件。
 *
 * @param pageNo 当前页码，从 1 开始
 * @param pageSize 每页数量
 * @param name 数据源名称模糊匹配条件
 * @param keyword 名称或 JDBC 地址关键字
 * @param dbType 数据库类型
 * @param environment 运行环境
 * @param connStatus 连通状态
 * @author weifuwan
 * @since 2026-09-25
 */
public record DataSourcePageQuery(
        int pageNo,
        int pageSize,
        String name,
        String keyword,
        String dbType,
        DataSourceEnvironment environment,
        DataSourceConnStatus connStatus) {}
