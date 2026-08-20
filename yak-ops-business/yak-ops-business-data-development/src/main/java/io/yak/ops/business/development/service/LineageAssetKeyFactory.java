package io.yak.ops.business.development.service;

import org.springframework.stereotype.Component;

/**
 * Centralizes lineage asset key generation after physical table identity resolution.
 *
 * <p>All lineage writers should generate TABLE/COLUMN keys through this component instead of
 * rebuilding canonical names locally. This keeps SQL lineage, dataset lineage and future metadata
 * ingestion consistent.
 */
@Component
public class LineageAssetKeyFactory {

  public String tableKey(TableIdentityResolver.PhysicalTableIdentity identity) {
    if (identity == null) {
      throw new IllegalArgumentException("physical table identity 不能为空");
    }
    return identity.assetKey();
  }

  public String columnKey(
      TableIdentityResolver.PhysicalTableIdentity identity,
      String columnName) {
    if (identity == null) {
      throw new IllegalArgumentException("physical table identity 不能为空");
    }
    if (columnName == null || columnName.isBlank()) {
      throw new IllegalArgumentException("columnName 不能为空");
    }
    return "column:%s.%s".formatted(
        identity.assetKey().substring("table:".length()),
        columnName.trim().toLowerCase(java.util.Locale.ROOT));
  }
}
