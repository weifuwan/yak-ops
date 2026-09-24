package io.yak.ops.business.datasource.connection;

import io.yak.ops.common.enums.datasource.DataSourceDbType;

/** Typed input for connection testing while preserving legacy optional datasource reference semantics. */
public record DataSourceConnectionRequest(Long dataSourceId, DataSourceDbType requestedType, String connectionJson) {}
