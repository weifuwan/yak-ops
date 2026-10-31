package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.factories.Factory;
import io.yak.ops.core.schema.Schema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Factory session carrying only the tables authorized and frozen by the submission boundary. */
public final class SourceFactoryContext implements Factory.Context {
    private final List<TableSourceFactoryContext> tables;
    private final Configuration pipelineConfiguration;
    private final ClassLoader classLoader;

    private final Map<TableId, Schema> schemas;

    public SourceFactoryContext(
            List<TableSourceFactoryContext> tables, Configuration pipelineConfiguration, ClassLoader classLoader) {
        this(tables, pipelineConfiguration, classLoader, Map.of());
    }

    public SourceFactoryContext(
            List<TableSourceFactoryContext> tables,
            Configuration pipelineConfiguration,
            ClassLoader classLoader,
            Map<TableId, Schema> schemas) {

        this.tables = List.copyOf(Objects.requireNonNull(tables, "tables"));
        if (this.tables.isEmpty()) {
            throw new IllegalArgumentException("Connector requires a nonempty table collection");
        }
        this.pipelineConfiguration =
                new Configuration(Objects.requireNonNull(pipelineConfiguration, "pipelineConfiguration"));
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");

        Map<TableId, Schema> resolved = new LinkedHashMap<>();
        for (TableSourceFactoryContext table : this.tables) {
            Schema schema = schemas.getOrDefault(table.table(), Schema.from(table.schema(), null, Map.of(), Map.of()));
            if (!schema.toTableSchema().equals(table.schema()) || resolved.putIfAbsent(table.table(), schema) != null) {
                throw new IllegalArgumentException("Source metadata does not match its authorized table binding");
            }
        }
        if (!resolved.keySet().containsAll(schemas.keySet())) {
            throw new IllegalArgumentException("Source metadata includes an unauthorized table");
        }
        this.schemas = Collections.unmodifiableMap(resolved);
    }

    public List<TableSourceFactoryContext> tables() {
        return tables;
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
        return "SourceFactoryContext{configuration=redacted}";
    }

    public Map<TableId, Schema> schemas() {
        return schemas;
    }
}
