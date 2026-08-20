package io.yak.ops.business.development.service;

import org.springframework.stereotype.Component;

/**
 * Centralizes lineage asset key generation.
 *
 * <p>All lineage publishers should generate metadata asset keys through this component to avoid
 * duplicate assets caused by different naming strategies.
 */
@Component
public class LineageAssetKeyFactory {

  public String tableKey(TableIdentityResolver.PhysicalTableIdentity identity) {
    if (identity == null) {
      throw new IllegalArgumentException("table identity 不能为空");
    }
    return "table:%s:%s.%s.%s".formatted(
        identity.dataSourceId(),
        identity.databaseName(),
        identity.schemaName(),
        identity.tableName());
  }

  public String columnKey(
      TableIdentityResolver.PhysicalTableIdentity identity,
      String columnName) {
    if (identity == null) {
      throw new IllegalArgumentException("table identity 不能为空");
    }
    return "column:%s:%s.%s.%s.%s".formatted(
        identity.dataSourceId(),
        identity.databaseName(),
        identity.schemaName(),
        identity.tableName(),
        columnName == null ? "" : columnName.toLowerCase(java.util.Locale.ROOT));
  }
}
