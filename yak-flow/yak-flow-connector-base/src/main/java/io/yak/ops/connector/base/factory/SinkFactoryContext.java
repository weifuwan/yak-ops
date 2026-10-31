package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.factories.Factory;
import io.yak.ops.core.schema.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** Factory session carrying only the tables authorized and frozen by the submission boundary. */
public final class SinkFactoryContext implements Factory.Context {
    private final List<TableSinkFactoryContext> tables;
    private final Configuration pipelineConfiguration;
    private final ClassLoader classLoader;
    private final BiFunction<TableId, Schema, Schema> sourceSchemaTransform;
    private final Map<TableId, Schema> sourceSchemas;
    private final boolean sourceProducesChangelog;

    public SinkFactoryContext(
            List<TableSinkFactoryContext> tables, Configuration pipelineConfiguration, ClassLoader classLoader) {
        this(tables, pipelineConfiguration, classLoader, (table, schema) -> schema, sourceSchemas(tables), true);
    }

    /**
     * Carries the same deterministic schema projection used by the pipeline's data transform.
     * The callback must not access credentials, mutate operator state or perform external I/O.
     * The source's configured mutation capability is independent of its boundedness.
     */
    public SinkFactoryContext(
            List<TableSinkFactoryContext> tables,
            Configuration pipelineConfiguration,
            ClassLoader classLoader,
            BiFunction<TableId, Schema, Schema> sourceSchemaTransform,
            Map<TableId, Schema> sourceSchemas,
            boolean sourceProducesChangelog) {

        this.tables = List.copyOf(Objects.requireNonNull(tables, "tables"));
        if (this.tables.isEmpty()) {
            throw new IllegalArgumentException("Connector requires a nonempty table collection");
        }
        this.pipelineConfiguration =
                new Configuration(Objects.requireNonNull(pipelineConfiguration, "pipelineConfiguration"));
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.sourceSchemaTransform = Objects.requireNonNull(sourceSchemaTransform, "sourceSchemaTransform");
        this.sourceSchemas = Map.copyOf(Objects.requireNonNull(sourceSchemas, "sourceSchemas"));
        this.sourceProducesChangelog = sourceProducesChangelog;
        if (!this.sourceSchemas
                .keySet()
                .equals(this.tables.stream()
                        .map(TableSinkFactoryContext::sourceTable)
                        .collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalArgumentException("Raw source schemas must cover exactly the admitted sink routes");
        }
    }

    public List<TableSinkFactoryContext> tables() {
        return tables;
    }

    public BiFunction<TableId, Schema, Schema> sourceSchemaTransform() {
        return sourceSchemaTransform;
    }

    /** Returns unprojected source metadata used to establish the initial checkpoint baseline. */
    public Map<TableId, Schema> sourceSchemas() {
        return sourceSchemas;
    }

    /** Whether the configured source can emit updates or deletes, including in a bounded job. */
    public boolean sourceProducesChangelog() {
        return sourceProducesChangelog;
    }

    private static Map<TableId, Schema> sourceSchemas(List<TableSinkFactoryContext> tables) {
        Map<TableId, Schema> schemas = new LinkedHashMap<>();
        for (TableSinkFactoryContext table : tables) {
            if (schemas.putIfAbsent(table.sourceTable(), Schema.from(table.sourceSchema(), null, Map.of(), Map.of()))
                    != null) {
                throw new IllegalArgumentException("Duplicate source table route");
            }
        }
        return schemas;
    }

    @Override
    public ReadonlyConfig getFactoryConfiguration() {
        return tables.getFirst().options();
    }

    @Override
    public Configuration getPipelineConfiguration() {
        return new Configuration(pipelineConfiguration);
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public String toString() {
        return "SinkFactoryContext{configuration=redacted}";
    }
}
