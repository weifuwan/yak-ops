/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.cdc.common.factories;

import io.yak.ops.cdc.common.configuration.ConfigOption;
import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.configuration.FallbackKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Flink CDC factory configuration validation and context, without Flink Table/ReadableConfig.
 */
public class FactoryHelper {

    private final Factory factory;
    private final Factory.Context context;

    private FactoryHelper(Factory factory, Factory.Context context) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.context = Objects.requireNonNull(context, "context");
    }

    public static FactoryHelper createFactoryHelper(Factory factory, Factory.Context context) {
        return new FactoryHelper(factory, context);
    }

    public static void validateFactoryOptions(Factory factory, Configuration configuration) {
        validateFactoryOptions(factory.requiredOptions(), factory.optionalOptions(), configuration);
    }

    public static void validateFactoryOptions(
            Set<ConfigOption<?>> requiredOptions,
            Set<ConfigOption<?>> optionalOptions,
            Configuration configuration) {
        List<String> missingRequiredOptions =
                requiredOptions.stream()
                        .filter(option -> configuration.get(option) == null)
                        .flatMap(FactoryHelper::allKeys)
                        .sorted()
                        .collect(Collectors.toList());

        if (!missingRequiredOptions.isEmpty()) {
            throw new IllegalArgumentException(
                    "One or more required options are missing.\n\nMissing required options are:\n\n"
                            + String.join("\n", missingRequiredOptions));
        }
        optionalOptions.forEach(configuration::getOptional);
    }

    public static void validateUnconsumedKeys(
            String factoryIdentifier, Set<String> allOptionKeys, Set<String> consumedOptionKeys) {
        Set<String> remainingOptionKeys = new HashSet<>(allOptionKeys);
        remainingOptionKeys.removeAll(consumedOptionKeys);
        if (!remainingOptionKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Unsupported options found for '%s'.\n\nUnsupported options:\n\n%s\n\nSupported options:\n\n%s",
                            factoryIdentifier,
                            remainingOptionKeys.stream().sorted().collect(Collectors.joining("\n")),
                            consumedOptionKeys.stream().sorted().collect(Collectors.joining("\n"))));
        }
    }

    public void validate() {
        Set<String> allOptionKeys =
                Stream.concat(
                                factory.requiredOptions().stream().flatMap(FactoryHelper::allKeys),
                                factory.optionalOptions().stream().flatMap(FactoryHelper::allKeys))
                        .collect(Collectors.toSet());
        validateFactoryOptions(factory, context.getFactoryConfiguration());
        validateUnconsumedKeys(
                factory.identifier(), context.getFactoryConfiguration().getKeys(), allOptionKeys);
    }

    public void validateExcept(String... prefixesToSkip) {
        if (prefixesToSkip.length == 0) {
            throw new IllegalArgumentException("Prefixes to skip cannot be empty.");
        }
        List<String> prefixesList = Arrays.asList(prefixesToSkip);
        Set<String> allOptionKeys =
                Stream.concat(
                                factory.requiredOptions().stream().flatMap(FactoryHelper::allKeys),
                                factory.optionalOptions().stream().flatMap(FactoryHelper::allKeys))
                        .collect(Collectors.toSet());
        Set<String> filteredKeys =
                context.getFactoryConfiguration().getKeys().stream()
                        .filter(key -> prefixesList.stream().noneMatch(key::startsWith))
                        .collect(Collectors.toSet());
        validateFactoryOptions(factory, context.getFactoryConfiguration());
        validateUnconsumedKeys(factory.identifier(), filteredKeys, allOptionKeys);
    }

    private static Stream<String> allKeys(ConfigOption<?> option) {
        return Stream.concat(
                Stream.of(option.key()),
                StreamSupport.stream(option.fallbackKeys().spliterator(), false)
                        .map(FallbackKey::getKey));
    }

    /** Returns configuration options whose names begin with the specified format prefix. */
    public Configuration getFormatConfig(String formatPrefix) {
        String prefix = formatPrefix + ".";
        Map<String, String> options = new HashMap<>();
        context.getFactoryConfiguration().toMap().forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                options.put(key.substring(prefix.length()), value);
            }
        });
        return Configuration.fromMap(options);
    }

    /** Context for standalone CDC factories; no Flink runtime configuration is required. */
    public static class DefaultContext implements Factory.Context {
        private final Configuration factoryConfiguration;
        private final Configuration pipelineConfiguration;
        private final ClassLoader classLoader;

        public DefaultContext(
                Configuration factoryConfiguration,
                Configuration pipelineConfiguration,
                ClassLoader classLoader) {
            this.factoryConfiguration =
                    Objects.requireNonNull(factoryConfiguration, "factoryConfiguration");
            this.pipelineConfiguration =
                    Objects.requireNonNull(pipelineConfiguration, "pipelineConfiguration");
            this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        }

        @Override
        public Configuration getFactoryConfiguration() {
            return factoryConfiguration;
        }

        @Override
        public Configuration getPipelineConfiguration() {
            return pipelineConfiguration;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }
}
