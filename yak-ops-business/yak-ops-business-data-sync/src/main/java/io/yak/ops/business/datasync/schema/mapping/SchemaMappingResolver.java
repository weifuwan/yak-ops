package io.yak.ops.business.datasync.schema.mapping;

import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把任务级 Column Mapping 解析为位置对齐的 Source Read Schema 与 Target Write Schema。
 *
 * <p>Mapping 为空时保持大小写不敏感同名映射；显式 Mapping 的数组顺序就是 YakRow 字段位置顺序。
 *
 * @author weifuwan
 * @since 2026-10-05
 */
public final class SchemaMappingResolver {

    public ResolvedSchemaMapping resolve(LogicalTable sourceTable, List<SchemaColumnMapping> configuredMappings) {
        Map<String, LogicalColumn> sourceByName = sourceColumns(sourceTable);
        List<SchemaColumnMapping> mappings =
                configuredMappings == null ? sameNameMappings(sourceTable) : normalizeMappings(configuredMappings);

        List<LogicalColumn> sourceColumns = new ArrayList<>(mappings.size());
        List<LogicalColumn> targetColumns = new ArrayList<>(mappings.size());
        Map<String, String> targetBySource = new HashMap<>();

        for (SchemaColumnMapping mapping : mappings) {
            LogicalColumn sourceColumn = sourceByName.get(normalize(mapping.source()));
            if (sourceColumn == null) {
                throw new IllegalArgumentException("来源表不存在映射字段：" + mapping.source());
            }
            sourceColumns.add(sourceColumn);
            targetColumns.add(new LogicalColumn(
                    mapping.target(),
                    sourceColumn.dataType(),
                    sourceColumn.nullable(),
                    sourceColumn.length(),
                    sourceColumn.comment()));
            targetBySource.put(normalize(sourceColumn.name()), mapping.target());
        }

        boolean allPrimaryKeysMapped = sourceTable.primaryKeys().stream()
                .allMatch(primaryKey -> targetBySource.containsKey(normalize(primaryKey)));
        List<String> sourcePrimaryKeys = allPrimaryKeysMapped
                ? sourceTable.primaryKeys().stream()
                        .map(primaryKey ->
                                sourceByName.get(normalize(primaryKey)).name())
                        .toList()
                : List.of();
        List<String> targetPrimaryKeys = allPrimaryKeysMapped
                ? sourceTable.primaryKeys().stream()
                        .map(primaryKey -> targetBySource.get(normalize(primaryKey)))
                        .toList()
                : List.of();

        LogicalTable projectedSource = new LogicalTable(
                sourceTable.name(),
                sourceTable.comment(),
                sourceTable.schemaVersion(),
                sourceColumns,
                sourcePrimaryKeys);
        LogicalTable projectedTarget = new LogicalTable(
                sourceTable.name(),
                sourceTable.comment(),
                sourceTable.schemaVersion(),
                targetColumns,
                targetPrimaryKeys);
        return new ResolvedSchemaMapping(projectedSource, projectedTarget, mappings);
    }

    private Map<String, LogicalColumn> sourceColumns(LogicalTable sourceTable) {
        Map<String, LogicalColumn> result = new LinkedHashMap<>();
        for (LogicalColumn column : sourceTable.columns()) {
            LogicalColumn previous = result.put(normalize(column.name()), column);
            if (previous != null) {
                throw new IllegalArgumentException("来源表存在大小写不敏感重名字段：" + previous.name() + " / " + column.name());
            }
        }
        return result;
    }

    private List<SchemaColumnMapping> sameNameMappings(LogicalTable sourceTable) {
        return sourceTable.columns().stream()
                .map(column -> new SchemaColumnMapping(column.name(), column.name()))
                .toList();
    }

    private List<SchemaColumnMapping> normalizeMappings(List<SchemaColumnMapping> mappings) {
        if (mappings.isEmpty()) throw new IllegalArgumentException("字段映射不能为空");

        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        List<SchemaColumnMapping> result = new ArrayList<>(mappings.size());
        for (SchemaColumnMapping mapping : mappings) {
            if (mapping == null) throw new IllegalArgumentException("字段映射不能包含空项");
            String source = mapping.source().trim();
            String target = mapping.target().trim();
            if (!sources.add(normalize(source))) {
                throw new IllegalArgumentException("来源字段不能重复映射：" + source);
            }
            if (!targets.add(normalize(target))) {
                throw new IllegalArgumentException("目标字段不能被重复映射：" + target);
            }
            result.add(new SchemaColumnMapping(source, target));
        }
        return List.copyOf(result);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
