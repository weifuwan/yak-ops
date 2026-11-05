package io.yak.ops.plugin.datasource.api.datasource;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * An optional, driver-provided connection property suggestion.
 * Default values are hints only and must not be persisted automatically.
 */
@Getter
@AllArgsConstructor
public class ConnectionProperty {

    private final String name;

    private final String description;

    private final String defaultValue;

    private final boolean required;

    private final List<String> choices;
}
