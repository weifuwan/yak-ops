package org.apache.flink.cdc.runtime.operators.transform;

import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.cdc.common.source.SupportedMetadataColumn;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builder of {@link PreTransformOperator}. */
public class PreTransformOperatorBuilder {
    private final List<TransformRule> transformRules = new ArrayList<>();

    private final List<Tuple3<String, String, Map<String, String>>> udfFunctions =
            new ArrayList<>();

    public PreTransformOperatorBuilder addTransform(
            String tableInclusions, @Nullable String projection, @Nullable String filter) {
        transformRules.add(
                new TransformRule(
                        tableInclusions,
                        projection,
                        filter,
                        "",
                        "",
                        "",
                        ",",
                        null,
                        new SupportedMetadataColumn[0]));
        return this;
    }

    public PreTransformOperatorBuilder addTransform(
            String tableInclusions,
            @Nullable String projection,
            @Nullable String filter,
            String primaryKey,
            String partitionKey,
            String tableOption,
            @Nullable String postTransformConverter,
            SupportedMetadataColumn[] supportedMetadataColumns) {
        return addTransform(
                tableInclusions,
                projection,
                filter,
                primaryKey,
                partitionKey,
                tableOption,
                ",",
                postTransformConverter,
                supportedMetadataColumns);
    }

    public PreTransformOperatorBuilder addTransform(
            String tableInclusions,
            @Nullable String projection,
            @Nullable String filter,
            String primaryKey,
            String partitionKey,
            String tableOption,
            String tableOptionsDelimiter,
            @Nullable String postTransformConverter,
            SupportedMetadataColumn[] supportedMetadataColumns) {
        transformRules.add(
                new TransformRule(
                        tableInclusions,
                        projection,
                        filter,
                        primaryKey,
                        partitionKey,
                        tableOption,
                        tableOptionsDelimiter,
                        postTransformConverter,
                        supportedMetadataColumns));
        return this;
    }

    public PreTransformOperatorBuilder addUdfFunctions(
            List<Tuple3<String, String, Map<String, String>>> udfFunctions) {
        this.udfFunctions.addAll(udfFunctions);
        return this;
    }

    public PreTransformOperator build() {
        return new PreTransformOperator(transformRules, udfFunctions);
    }
}
