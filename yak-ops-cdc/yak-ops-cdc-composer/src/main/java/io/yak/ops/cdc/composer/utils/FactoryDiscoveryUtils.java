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

package io.yak.ops.cdc.composer.utils;

import io.yak.ops.cdc.common.factories.Factory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Discover CDC factories using Java SPI, aligned with Flink CDC's FactoryDiscoveryUtils.
 * Standalone execution does not upload connector JARs to a Flink environment.
 */
public final class FactoryDiscoveryUtils {

    private FactoryDiscoveryUtils() {}

    public static <T extends Factory> T getFactoryByIdentifier(
            String identifier, Class<T> factoryClass) {
        return getFactoryByIdentifier(
                identifier, factoryClass, Thread.currentThread().getContextClassLoader());
    }

    public static <T extends Factory> T getFactoryByIdentifier(
            String identifier, Class<T> factoryClass, ClassLoader classLoader) {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(factoryClass, "factoryClass");
        Objects.requireNonNull(classLoader, "classLoader");
        ServiceLoader<Factory> loader = ServiceLoader.load(Factory.class, classLoader);
        List<Factory> matches = new ArrayList<>();
        List<String> available = new ArrayList<>();
        for (Factory factory : loader) {
            available.add(factory.getClass().getName());
            if (factory.identifier().equals(identifier)
                    && factoryClass.isAssignableFrom(factory.getClass())) {
                matches.add(factory);
            }
        }
        if (matches.isEmpty()) {
            throw new IllegalStateException(
                    String.format(
                            "Cannot find factory with identifier \"%s\" in the classpath. Available factory classes are: %s",
                            identifier,
                            available.stream().sorted().collect(Collectors.joining(", "))));
        }
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    String.format(
                            "Multiple factories found for identifier \"%s\": %s",
                            identifier,
                            matches.stream()
                                    .map(f -> f.getClass().getName())
                                    .sorted()
                                    .collect(Collectors.joining(", "))));
        }
        return factoryClass.cast(matches.get(0));
    }
}
