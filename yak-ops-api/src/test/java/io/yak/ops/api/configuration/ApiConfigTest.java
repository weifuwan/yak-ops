package io.yak.ops.api.configuration;

import org.apache.commons.collections4.MapUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ApiConfig.class)
public class ApiConfigTest {

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
