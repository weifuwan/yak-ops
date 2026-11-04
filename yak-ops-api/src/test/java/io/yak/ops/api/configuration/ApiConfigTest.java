package io.yak.ops.api.configuration;

import io.yak.ops.api.controller.AbstractControllerTest;

import org.apache.commons.collections4.MapUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ApiConfigTest extends AbstractControllerTest {

    @Autowired
    private ApiConfig apiConfig;

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
