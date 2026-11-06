package io.yak.ops.api.configuration;

import org.apache.commons.collections4.MapUtils;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

public class ApiConfigTest {

    private static ApiConfig apiConfig;

    @BeforeAll
    public static void loadConfig() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        new YamlPropertySourceLoader().load("api-test", new ClassPathResource("application.yaml"))
                .forEach(sources::addLast);
        apiConfig = new Binder(ConfigurationPropertySources.from(sources))
                .bind("api", Bindable.of(ApiConfig.class))
                .get();
    }

    @Test
    public void testIsAuditEnable() {
        Assertions.assertTrue(apiConfig.isAuditEnable());
    }

    @Test
    public void testGetTrafficControlConfig() {
        ApiConfig.TrafficConfiguration trafficControl = apiConfig.getTrafficControl();

        Assertions.assertFalse(trafficControl.isGlobalSwitch());
        Assertions.assertEquals(299, (int) trafficControl.getMaxGlobalQpsRate());
        Assertions.assertFalse(trafficControl.isTenantSwitch());
        Assertions.assertEquals(9, (int) trafficControl.getDefaultTenantQpsRate());
        Assertions.assertTrue(MapUtils.isEmpty(trafficControl.getCustomizeTenantQpsRate()));
    }

    @Test
    public void testGetPythonGateway() {
        ApiConfig.PythonGatewayConfiguration pythonGateway = apiConfig.getPythonGateway();

        Assertions.assertFalse(pythonGateway.isEnabled());
    }

}
