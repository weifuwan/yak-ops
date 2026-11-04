package org.apache.flink.cdc.runtime.operators.transform;

import org.apache.flink.cdc.common.source.SupportedMetadataColumn;
import org.apache.flink.cdc.common.utils.StringUtils;

import javax.annotation.Nullable;

import java.io.Serializable;

/** A rule defining pre-transformations where filtered rows and irrelevant columns are removed. */
public class TransformRule implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String tableInclusions;
    private final @Nullable String projection;
    private final @Nullable String filter;
    private final String primaryKey;
    private final String partitionKey;
    private final String tableOption;
    private final String tableOptionsDelimiter;
    private final @Nullable String postTransformConverter;
    private final SupportedMetadataColumn[] supportedMetadataColumns;

    public TransformRule(
            String tableInclusions,
            @Nullable String projection,
            @Nullable String filter,
            String primaryKey,
            String partitionKey,
            String tableOption,
            String tableOptionsDelimiter,
            @Nullable String postTransformConverter,
            SupportedMetadataColumn[] supportedMetadataColumns) {
        this.tableInclusions = tableInclusions;
        this.projection = StringUtils.isNullOrWhitespaceOnly(projection) ? "*" : projection;
        this.filter = filter;
        this.primaryKey = primaryKey;
        this.partitionKey = partitionKey;
        this.tableOption = tableOption;
        this.tableOptionsDelimiter = tableOptionsDelimiter;
        this.postTransformConverter = postTransformConverter;
        this.supportedMetadataColumns = supportedMetadataColumns;
    }

    public String getTableInclusions() {
        return tableInclusions;
    }

    @Nullable
    public String getProjection() {
        return projection;
    }

    @Nullable
    public String getFilter() {
        return filter;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getTableOption() {
        return tableOption;
    }

    public String getTableOptionsDelimiter() {
        return tableOptionsDelimiter;
    }

    @Nullable
    public String getPostTransformConverter() {
        return postTransformConverter;
    }

    public SupportedMetadataColumn[] getSupportedMetadataColumns() {
        return supportedMetadataColumns;
    }
}
