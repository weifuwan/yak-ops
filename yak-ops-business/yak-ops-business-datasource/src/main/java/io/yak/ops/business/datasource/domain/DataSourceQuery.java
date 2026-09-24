package io.yak.ops.business.datasource.domain;

import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;

/** 数据源领域查询条件。 */
public record DataSourceQuery(
        int pageNo,
        int pageSize,
        String name,
        String keyword,
        DataSourceDbType dbType,
        DataSourceEnvironment environment,
        DataSourceConnStatus connStatus) {}
