package io.yak.ops.plugin.task.api.utils;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.Constructor;

import lombok.extern.slf4j.Slf4j;

/**
 * Whitelist constructor implementation for YAML snake.
 * Copied from Apache ShardingSphere and Apache Skywalking.
 */
@Slf4j
public final class ClassFilterConstructor extends Constructor {

    private final Class<?>[] acceptClasses;

    public ClassFilterConstructor(final Class<?>[] acceptClasses) {
        super(new LoaderOptions());
        this.acceptClasses = acceptClasses;
    }

    @Override
    protected Class<?> getClassForName(final String name) throws ClassNotFoundException {
        for (Class<? extends Object> each : acceptClasses) {
            if (name.equals(each.getName())) {
                log.info("name - {} : class - {}", name, super.getClassForName(name));
                return super.getClassForName(name);
            }
        }
        throw new IllegalArgumentException(String.format("Class is not accepted: %s", name));
    }
}
