package io.yak.ops.plugin.alert.script;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.spi.params.base.PluginParams;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ScriptAlertChannelFactoryTest {

    @Test
    public void testGetParams() {
        ScriptAlertChannelFactory scriptAlertChannelFactory = new ScriptAlertChannelFactory();
        List<PluginParams> params = scriptAlertChannelFactory.params();
        Assertions.assertEquals(3, params.size());
    }

    @Test
    public void testCreate() {
        ScriptAlertChannelFactory scriptAlertChannelFactory = new ScriptAlertChannelFactory();
        AlertChannel alertChannel = scriptAlertChannelFactory.create();
        Assertions.assertNotNull(alertChannel);
    }
}
