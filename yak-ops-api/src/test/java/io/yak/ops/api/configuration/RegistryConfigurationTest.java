package io.yak.ops.api.configuration;

import io.yak.ops.api.controller.AbstractControllerTest;
import io.yak.ops.plugin.registry.jdbc.JdbcRegistry;
import io.yak.ops.registry.api.Registry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RegistryConfigurationTest extends AbstractControllerTest {

    @Autowired
    private Registry registry;

    @Test
    public void usesJdbcRegistry() {
        Assertions.assertInstanceOf(JdbcRegistry.class, registry);
    }
}
