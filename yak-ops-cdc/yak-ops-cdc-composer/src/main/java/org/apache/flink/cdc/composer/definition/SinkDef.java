package org.apache.flink.cdc.composer.definition;

import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.cdc.common.event.SchemaChangeEventType;
import org.apache.flink.cdc.common.event.SchemaChangeEventTypeFamily;
import org.apache.flink.cdc.common.pipeline.ExistingTableSchemaExpansionMode;

import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Definition of a data sink.
 *
 * <p>A sink definition contains:
 *
 * <ul>
 *   <li>type: connector type of the sink, which will be used for discovering sink implementation.
 *       Required in the definition.
 *   <li>name: name of the sink. Optional in the definition.
 *   <li>config: configuration of the sink
 * </ul>
 */
public class SinkDef {
    private final String type;
    @Nullable private final String name;
    private final Configuration config;
    private final Set<SchemaChangeEventType> includedSchemaEvolutionTypes;
    private final ExistingTableSchemaExpansionMode existingTableSchemaExpansionMode;

    public SinkDef(String type, @Nullable String name, Configuration config) {
        this.type = type;
        this.name = name;
        this.config = config;
        this.includedSchemaEvolutionTypes =
                Arrays.stream(SchemaChangeEventTypeFamily.ALL).collect(Collectors.toSet());
        this.existingTableSchemaExpansionMode = ExistingTableSchemaExpansionMode.DISABLED;
    }

    public SinkDef(
            String type,
            @Nullable String name,
            Configuration config,
            Set<SchemaChangeEventType> includedSchemaEvolutionTypes) {
        this(
                type,
                name,
                config,
                includedSchemaEvolutionTypes,
                ExistingTableSchemaExpansionMode.DISABLED);
    }

    public SinkDef(
            String type,
            @Nullable String name,
            Configuration config,
            Set<SchemaChangeEventType> includedSchemaEvolutionTypes,
            ExistingTableSchemaExpansionMode existingTableSchemaExpansionMode) {
        this.type = type;
        this.name = name;
        this.config = config;
        this.includedSchemaEvolutionTypes = includedSchemaEvolutionTypes;
        this.existingTableSchemaExpansionMode = existingTableSchemaExpansionMode;
    }

    public String getType() {
        return type;
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public Configuration getConfig() {
        return config;
    }

    public Set<SchemaChangeEventType> getIncludedSchemaEvolutionTypes() {
        return includedSchemaEvolutionTypes;
    }

    public ExistingTableSchemaExpansionMode getExistingTableSchemaExpansionMode() {
        return existingTableSchemaExpansionMode;
    }

    @Override
    public String toString() {
        return "SinkDef{"
                + "type='"
                + type
                + '\''
                + ", name='"
                + name
                + '\''
                + ", config="
                + config
                + ", includedSchemaEvolutionTypes="
                + includedSchemaEvolutionTypes
                + ", existingTableSchemaExpansionMode="
                + existingTableSchemaExpansionMode
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SinkDef sinkDef = (SinkDef) o;
        return Objects.equals(type, sinkDef.type)
                && Objects.equals(name, sinkDef.name)
                && Objects.equals(config, sinkDef.config)
                && Objects.equals(
                        includedSchemaEvolutionTypes, sinkDef.includedSchemaEvolutionTypes)
                && existingTableSchemaExpansionMode == sinkDef.existingTableSchemaExpansionMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                type, name, config, includedSchemaEvolutionTypes, existingTableSchemaExpansionMode);
    }
}
