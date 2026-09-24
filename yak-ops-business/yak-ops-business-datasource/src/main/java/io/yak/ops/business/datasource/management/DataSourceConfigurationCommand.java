package io.yak.ops.business.datasource.management;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;

/** Typed command for creating or updating datasource configuration. */
public record DataSourceConfigurationCommand(
        String name,
        DataSourceDbType dbType,
        DataSourceEnvironment environment,
        String remark,
        String connectionJson) {}
