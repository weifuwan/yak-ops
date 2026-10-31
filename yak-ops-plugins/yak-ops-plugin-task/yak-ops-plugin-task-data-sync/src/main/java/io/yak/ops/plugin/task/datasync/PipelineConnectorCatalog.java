package io.yak.ops.plugin.task.datasync;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.composer.definition.PipelineOptions;
import io.yak.ops.composer.definition.TypeMappingRules;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.factories.ConnectorFeature;
import io.yak.ops.core.factories.DataSinkFactory;
import io.yak.ops.core.factories.DataSourceFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only Connector capabilities for the Task editor; execution uses the selected factory identifiers. */
public final class PipelineConnectorCatalog {
    private final FactoryUtil factories;

    public PipelineConnectorCatalog(FactoryUtil factories) {
        this.factories = Objects.requireNonNull(factories, "factories");
    }

    /**
     * Lists installed factories matching authorized datasource type selections without reading credentials.
     *
     * @param sourceType product source datasource type
     * @param targetType product target datasource type
     * @return immutable source and sink capability pairs; no resource authorization is implied
     */
    public List<ConnectorPair> list(String sourceType, String targetType) {
        if (sourceType == null || sourceType.isBlank() || targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("Datasource types are required");
        }
        List<ConnectorPair> pairs = new ArrayList<>();
        for (DataSourceFactory source : factories.sourceFactories()) {
            if (!sourceType.equals(source.datasourceType())) continue;
            for (DataSinkFactory sink : factories.sinkFactories()) {
                if (!targetType.equals(sink.datasourceType())) continue;
                pairs.add(new ConnectorPair(source, sink));
            }
        }
        return List.copyOf(pairs);
    }

    /** Connector-owned behavior used to present form choices, never to create the execution graph. */
    public static final class ConnectorPair {
        private final String sourceConnector;
        private final String sinkConnector;
        private final boolean producesChangelog;
        private final boolean supportsIncrementalBatch;
        private final boolean supportsSchemaChanges;
        private final boolean supportsRetract;
        private final String boundedness;
        private final Map<String, Object> capabilities;

        private ConnectorPair(DataSourceFactory source, DataSinkFactory sink) {
            this.sourceConnector = source.identifier();
            this.sinkConnector = sink.identifier();
            this.producesChangelog = source.producesChangelog();
            this.supportsIncrementalBatch = source.supportsIncrementalBatch();
            this.supportsSchemaChanges = source.supportsSchemaMetadata() && sink.supportsSchemaChanges();
            this.supportsRetract = sink.supportsRetract();
            this.boundedness =
                    source.boundedness(ReadonlyConfig.fromMap(Map.of())).name();
            this.capabilities = capabilities(source, sink);
        }

        @JsonProperty("sourceConnector")
        public String sourceConnector() {
            return sourceConnector;
        }

        @JsonProperty("sinkConnector")
        public String sinkConnector() {
            return sinkConnector;
        }

        @JsonProperty("producesChangelog")
        public boolean producesChangelog() {
            return producesChangelog;
        }

        @JsonProperty("supportsIncrementalBatch")
        public boolean supportsIncrementalBatch() {
            return supportsIncrementalBatch;
        }

        @JsonProperty("supportsSchemaChanges")
        public boolean supportsSchemaChanges() {
            return supportsSchemaChanges;
        }

        @JsonProperty("supportsRetract")
        public boolean supportsRetract() {
            return supportsRetract;
        }

        @JsonProperty("boundedness")
        public String boundedness() {
            return boundedness;
        }

        @JsonProperty("capabilities")
        public Map<String, Object> capabilities() {
            return capabilities;
        }

        private static Map<String, Object> capabilities(DataSourceFactory source, DataSinkFactory sink) {
            Set<ConnectorFeature> reader = source.supportedFeatures();
            Set<ConnectorFeature> writer = sink.supportedFeatures();
            boolean jdbcRead = reader.contains(ConnectorFeature.READ_PREDICATE);
            boolean hooks = writer.contains(ConnectorFeature.SQL_HOOKS) && !source.producesChangelog();
            List<String> modes = new ArrayList<>(List.of("APPEND", "UPSERT"));
            if (sink.supportsRetract() && source.producesChangelog()) modes.add("RETRACT");
            if (writer.contains(ConnectorFeature.REPLACE_WRITE) && !source.producesChangelog()) modes.add("REPLACE");
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("maxTables", PipelineOptions.MAX_TABLES);
            values.put("sourceRead", jdbcRead);
            values.put("explicitSplitKey", reader.contains(ConnectorFeature.EXPLICIT_SPLIT_KEY));
            values.put("dataPreview", reader.contains(ConnectorFeature.PREVIEW));
            values.put("sqlHooks", hooks);
            values.put(
                    "regexSelection", reader.contains(ConnectorFeature.DYNAMIC_TABLES) && sink.supportsSchemaChanges());
            values.put("snapshotFilter", reader.contains(ConnectorFeature.SNAPSHOT_FILTER));
            values.put("snapshotSkip", reader.contains(ConnectorFeature.SNAPSHOT_SKIP));
            values.put("dmlEvents", reader.contains(ConnectorFeature.DML_FILTER));
            values.put("typeMapping", writer.contains(ConnectorFeature.TYPE_MAPPING));
            values.put(
                    "typeMappingRules",
                    writer.contains(ConnectorFeature.TYPE_MAPPING) ? TypeMappingRules.supportedRules() : List.of());
            values.put("writeModes", List.copyOf(modes));
            values.put("sourceEngineKeys", jdbcRead ? List.of("fetchSize", "queryTimeoutSeconds") : List.of());
            values.put(
                    "sinkEngineKeys", writer.contains(ConnectorFeature.SQL_HOOKS) ? List.of("batchSize") : List.of());
            return Map.copyOf(values);
        }
    }
}
