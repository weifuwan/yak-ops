package io.yak.ops.plugin.alert.http;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.spi.params.base.PluginParams;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HttpAlertChannelFactoryTest {

    private HttpAlertChannelFactory httpAlertChannelFactory;

    @BeforeEach
    public void init() {
        httpAlertChannelFactory = new HttpAlertChannelFactory();
    }

    @Test
    public void getParamsTest() {

        List<PluginParams> pluginParamsList = httpAlertChannelFactory.params();
        Assertions.assertEquals(6, pluginParamsList.size());
    }

    @Test
    public void createTest() {
        AlertChannel alertChannel = httpAlertChannelFactory.create();
        Assertions.assertNotNull(alertChannel);
    }

}
