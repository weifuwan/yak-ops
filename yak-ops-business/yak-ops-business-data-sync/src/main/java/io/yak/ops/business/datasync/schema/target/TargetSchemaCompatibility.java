package io.yak.ops.business.datasync.schema.target;

import io.yak.ops.business.datasync.catalog.DataSyncCatalogColumns;
import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.core.types.Column;
import io.yak.ops.plugin.database.jdbc.schema.JdbcSchemaCompatibility;
import io.yak.ops.plugin.database.jdbc.schema.JdbcSchemaMapper;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 校验产品 LogicalTable 是否可以直接写入一个已存在 JDBC 目标表。
 *
 * <p>调用方先把任务 Mapping 投影为目标命名的 LogicalTable；本类再按该 LogicalTable 顺序匹配真实目标字段，类型兼容统一复用 JdbcSchemaCompatibility。</p>
 *
 * @author weifuwan
 * @since 2026-10-04
 */
public final class TargetSchemaCompatibility {

    private TargetSchemaCompatibility() {}

    public static TargetSchemaCompatibilityResult check(
            LogicalTable logicalTable, List<DataSourceCatalogColumnVO> targetColumns) {
        Objects.requireNonNull(logicalTable, "logicalTable must not be null");
        Objects.requireNonNull(targetColumns, "targetColumns must not be null");

        Map<String, DataSourceCatalogColumnVO> targetByName = DataSyncCatalogColumns.indexByName(targetColumns);
        List<DataSourceColumn> mappedTargetColumns =
                new ArrayList<>(logicalTable.columns().size());
        List<String> issues = new ArrayList<>();
        Set<String> mappedTargetNames = new HashSet<>();

        for (int index = 0; index < logicalTable.columns().size(); index++) {
            LogicalColumn source = logicalTable.columns().get(index);
            DataSourceCatalogColumnVO target = DataSyncCatalogColumns.findByName(targetByName, source.name());
            if (target == null) {
                issues.add("目标表缺少字段：" + source.name());
                continue;
            }
            mappedTargetNames.add(target.getName().toLowerCase(Locale.ROOT));

            DataSourceColumn targetColumn = DataSyncCatalogColumns.toColumn(target, index + 1);
            if (targetColumn == null) {
                issues.add("目标字段元数据不完整：" + source.name());
                continue;
            }

            Column sourceColumn =
                    new Column(source.name(), source.dataType(), source.nullable(), source.length());
            Column targetLogicalColumn;
            try {
                targetLogicalColumn = JdbcSchemaMapper.toColumn(targetColumn);
            } catch (IllegalArgumentException exception) {
                issues.add("目标字段类型不支持：" + source.name() + "（" + target.getTypeName() + "）");
                continue;
            }

            if (!JdbcSchemaCompatibility.isCompatible(sourceColumn, targetLogicalColumn)) {
                issues.add("目标字段不兼容：" + source.name() + "（" + source.dataType().kind() + " → "
                        + targetLogicalColumn.dataType().kind() + "）");
                continue;
            }
            mappedTargetColumns.add(targetColumn);
        }

        for (DataSourceCatalogColumnVO target : targetColumns) {
            if (target == null || target.getName() == null) continue;
            if (mappedTargetNames.contains(target.getName().toLowerCase(Locale.ROOT))) continue;
            if (!Boolean.TRUE.equals(target.getNullable())) {
                issues.add("目标表存在来源未映射的必填字段：" + target.getName());
            }
        }

        if (!issues.isEmpty()) {
            return new TargetSchemaCompatibilityResult(false, null, issues);
        }
        return new TargetSchemaCompatibilityResult(true, JdbcSchemaMapper.fromColumns(mappedTargetColumns), List.of());
    }
}
