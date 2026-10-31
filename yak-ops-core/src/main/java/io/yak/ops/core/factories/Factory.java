package io.yak.ops.core.factories;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.configuration.rule.RequiredOption;
import java.util.LinkedHashSet;
import java.util.Set;

/** SPI identity and configuration contract shared by DataSource and DataSink factories. */
public interface Factory {
    String identifier();

    /** Physical datasource kind accepted by this connector. */
    String datasourceType();

    /** Public operation options, validated before resolving any connection credentials. */
    OptionRule optionRule();

    /** Executable features supplied by this connector, independent of product presentation. */
    default Set<ConnectorFeature> supportedFeatures() {
        return Set.of();
    }

    /** Complete connector creation contract, including authorized connection settings. */
    default OptionRule creationOptionRule() {
        return optionRule();
    }

    default Set<Option<?>> requiredOptions() {
        Set<Option<?>> options = new LinkedHashSet<>();
        for (RequiredOption required : creationOptionRule().getRequiredOptions()) {
            if (required.kind() == RequiredOption.Kind.REQUIRED) {
                options.addAll(required.options());
            }
        }
        return Set.copyOf(options);
    }

    default Set<Option<?>> optionalOptions() {
        return Set.copyOf(creationOptionRule().getOptionalOptions());
    }

    default void validateDatasource(String datasourceType) {
        if (!datasourceType().equals(datasourceType)) {
            throw new IllegalArgumentException("Connector does not support the selected datasource type");
        }
    }

    /** Session inputs contain no Task, Workspace, DAO or credential lookup service. */
    interface Context {
        ReadonlyConfig getFactoryConfiguration();

        Configuration getPipelineConfiguration();

        ClassLoader getClassLoader();
    }
}
