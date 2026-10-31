package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.core.factories.DataSinkFactory;
import io.yak.ops.core.factories.DataSourceFactory;
import io.yak.ops.core.factories.Factory;
import io.yak.ops.spi.SpiRegistry;
import java.util.Collection;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Discovers stateless Connector factories and validates operation and creation inputs. */
public final class FactoryUtil {
    private final SpiRegistry<DataSourceFactory> sources;
    private final SpiRegistry<DataSinkFactory> sinks;

    public FactoryUtil() {
        sources = SpiRegistry.load(DataSourceFactory.class, FactoryUtil::identifiers);
        sinks = SpiRegistry.load(DataSinkFactory.class, FactoryUtil::identifiers);
    }

    public FactoryUtil(Iterable<? extends DataSourceFactory> sources, Iterable<? extends DataSinkFactory> sinks) {
        this.sources = SpiRegistry.of(sources, FactoryUtil::identifiers);
        this.sinks = SpiRegistry.of(sinks, FactoryUtil::identifiers);
    }

    public DataSourceFactory source(String identifier) {
        try {
            return sources.get(identifier);
        } catch (NoSuchElementException failure) {
            throw new IllegalArgumentException("Unknown Source factory identifier", failure);
        }
    }

    public DataSinkFactory sink(String identifier) {
        try {
            return sinks.get(identifier);
        } catch (NoSuchElementException failure) {
            throw new IllegalArgumentException("Unknown Sink factory identifier", failure);
        }
    }

    /** Registered source connectors, without synthetic aliases or fixed source/sink combinations. */
    public Collection<DataSourceFactory> sourceFactories() {
        return sources.types().stream().map(sources::get).toList();
    }

    /** Registered sink connectors selected independently of their source. */
    public Collection<DataSinkFactory> sinkFactories() {
        return sinks.types().stream().map(sinks::get).toList();
    }

    /** Validates caller-supplied operation options before connection settings are resolved. */
    public static void validate(ReadonlyConfig config, Factory factory, OptionRule common) {
        OptionRule rule = OptionRule.builder()
                .include(Objects.requireNonNull(common, "common"))
                .include(factory.optionRule())
                .build();
        validate(config, rule, factory.identifier());
    }

    /** Validates complete effective inputs before a Connector is constructed. */
    public static void validateCreation(ReadonlyConfig config, Factory factory) {
        validate(config, factory.creationOptionRule(), factory.identifier());
    }

    private static void validate(ReadonlyConfig config, OptionRule rule, String identifier) {
        ConfigValidator.of(config).validate(rule);
        ConfigValidator.validateUnknownKeys(config, rule, identifier);
    }

    private static Collection<String> identifiers(Factory factory) {
        if (factory.optionRule() == null || factory.creationOptionRule() == null) {
            throw new IllegalStateException("Connector Factory must declare option rules");
        }
        return Collections.singletonList(factory.identifier());
    }
}
