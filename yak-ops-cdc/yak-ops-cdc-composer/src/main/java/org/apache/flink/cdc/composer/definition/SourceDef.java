package org.apache.flink.cdc.composer.definition;

import org.apache.flink.cdc.common.configuration.Configuration;

import javax.annotation.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Definition of a data source.
 *
 * <p>A source definition contains:
 *
 * <ul>
 *   <li>type: connector type of the source, which will be used for discovering source
 *       implementation. Required in the definition.
 *   <li>name: name of the source. Optional in the definition.
 *   <li>config: configuration of the source
 * </ul>
 */
public class SourceDef {
    private final String type;
    @Nullable private final String name;
    private final Configuration config;

    public SourceDef(String type, @Nullable String name, Configuration config) {
        this.type = type;
        this.name = name;
        this.config = config;
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

    @Override
    public String toString() {
        return "SourceDef{"
                + "type='"
                + type
                + '\''
                + ", name='"
                + name
                + '\''
                + ", config="
                + config
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
        SourceDef sourceDef = (SourceDef) o;
        return Objects.equals(type, sourceDef.type)
                && Objects.equals(name, sourceDef.name)
                && Objects.equals(config, sourceDef.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, config);
    }
}
