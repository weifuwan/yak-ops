package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.spi.SpiRegistry;
import java.util.Collection;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Discovers stateless Connector factories and validates operation and creation inputs. */
public final class FactoryUtil {
    private final SpiRegistry<TableSourceFactory> sources;
    private final SpiRegistry<TableSinkFactory> sinks;

    public FactoryUtil() {
        sources = SpiRegistry.load(TableSourceFactory.class, FactoryUtil::identifiers);
        sinks = SpiRegistry.load(TableSinkFactory.class, FactoryUtil::identifiers);
    }

    public FactoryUtil(Iterable<? extends TableSourceFactory> sources, Iterable<? extends TableSinkFactory> sinks) {
        this.sources = SpiRegistry.of(sources, FactoryUtil::identifiers);
        this.sinks = SpiRegistry.of(sinks, FactoryUtil::identifiers);
    }

    public TableSourceFactory source(String identifier) {
        try {
            return sources.get(identifier);
        } catch (NoSuchElementException failure) {
            throw new IllegalArgumentException("Unknown Source factory identifier", failure);
        }
    }

    public TableSinkFactory sink(String identifier) {
        try {
            return sinks.get(identifier);
        } catch (NoSuchElementException failure) {
            throw new IllegalArgumentException("Unknown Sink factory identifier", failure);
        }
    }

    /** Validates caller-supplied operation options before connection settings are resolved. */
    public static void validate(ReadonlyConfig config, Factory factory, OptionRule common) {
        OptionRule rule = OptionRule.builder()
                .include(Objects.requireNonNull(common, "common"))
                .include(factory.optionRule())
                .build();
        validate(config, rule, factory.factoryIdentifier());
    }

    /** Validates complete effective inputs before a Connector is constructed. */
    public static void validateCreation(ReadonlyConfig config, Factory factory) {
        validate(config, factory.creationOptionRule(), factory.factoryIdentifier());
    }

    private static void validate(ReadonlyConfig config, OptionRule rule, String identifier) {
        ConfigValidator.of(config).validate(rule);
        ConfigValidator.validateUnknownKeys(config, rule, identifier);
    }

    private static Collection<String> identifiers(Factory factory) {
        if (factory.optionRule() == null || factory.creationOptionRule() == null) {
            throw new IllegalStateException("Connector Factory must declare option rules");
        }
        return Collections.singletonList(factory.factoryIdentifier());
    }
}
